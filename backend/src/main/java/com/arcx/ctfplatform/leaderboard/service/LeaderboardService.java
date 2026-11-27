package com.arcx.ctfplatform.leaderboard.service;

import com.arcx.ctfplatform.academic.entity.AcademicGroup;
import com.arcx.ctfplatform.academic.entity.GradingScale;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.entity.StudentStatus;
import com.arcx.ctfplatform.academic.repository.AcademicGroupRepository;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.academic.service.GradingService;
import com.arcx.ctfplatform.academic.service.StudentScoreCalculator;
import com.arcx.ctfplatform.attempts.entity.Attempt;
import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.competitions.entity.Competition;
import com.arcx.ctfplatform.competitions.repository.CompetitionRepository;
import com.arcx.ctfplatform.modifiers.entity.PromoCodeClaim;
import com.arcx.ctfplatform.modifiers.entity.ScoreAdjustment;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeClaimRepository;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeRepository;
import com.arcx.ctfplatform.modifiers.repository.ScoreAdjustmentRepository;
import com.arcx.ctfplatform.students.service.lab.LabScoreService;
import com.arcx.ctfplatform.tests.entity.QuizAttempt;
import com.arcx.ctfplatform.tests.entity.QuizQuestion;
import com.arcx.ctfplatform.tests.entity.Test;
import com.arcx.ctfplatform.tests.repository.QuizAttemptRepository;
import com.arcx.ctfplatform.tests.repository.QuizQuestionRepository;
import com.arcx.ctfplatform.tests.repository.TestRepository;
import com.arcx.ctfplatform.tests.service.KtScoreCalculator;
import com.arcx.ctfplatform.users.entity.Role;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.users.repository.UserRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LeaderboardService {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final LabScoreService labScoreService;
    private final GradingService gradingService;
    private final AttemptRepository attemptRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final TestRepository testRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final PromoCodeClaimRepository promoCodeClaimRepository;
    private final ScoreAdjustmentRepository scoreAdjustmentRepository;
    private final CompetitionRepository competitionRepository;
    private final AcademicGroupRepository academicGroupRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired(required = false)
    private QuizQuestionRepository quizQuestionRepository;

    public void setQuizQuestionRepository(QuizQuestionRepository quizQuestionRepository) {
        this.quizQuestionRepository = quizQuestionRepository;
    }

    private record Subscriber(
            SseEmitter emitter,
            String scopeType,
            UUID scopeId,
            boolean isAdmin,
            UUID studentId,
            String username,
            boolean eventsOnly
    ) {
    }

    private record ComputedRow(
            UUID studentId,
            UUID groupId,
            UUID streamId,
            StudentStatus status,
            boolean individuallyHidden,
            String studentUsername,
            LeaderboardEntry entry
    ) {
    }

    private record ProjectionContext(
            boolean leaderboardHiddenGlobally,
            List<ComputedRow> rows
    ) {
    }

    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();
    private final ExecutorService sseExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "leaderboard-sse");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean broadcastRunning = new AtomicBoolean(false);
    private final AtomicBoolean broadcastPending = new AtomicBoolean(false);

    @PreDestroy
    public void shutdownSseExecutor() {
        sseExecutor.shutdownNow();
    }

    public LeaderboardService(
            StudentRepository studentRepository,
            UserRepository userRepository,
            LabScoreService labScoreService,
            GradingService gradingService,
            AttemptRepository attemptRepository,
            QuizAttemptRepository quizAttemptRepository,
            TestRepository testRepository,
            PromoCodeRepository promoCodeRepository,
            PromoCodeClaimRepository promoCodeClaimRepository,
            ScoreAdjustmentRepository scoreAdjustmentRepository,
            CompetitionRepository competitionRepository,
            AcademicGroupRepository academicGroupRepository
    ) {
        this(
                studentRepository,
                userRepository,
                labScoreService,
                gradingService,
                attemptRepository,
                quizAttemptRepository,
                testRepository,
                promoCodeRepository,
                promoCodeClaimRepository,
                scoreAdjustmentRepository,
                competitionRepository,
                academicGroupRepository,
                null
        );
    }

    @Autowired
    public LeaderboardService(
            StudentRepository studentRepository,
            UserRepository userRepository,
            LabScoreService labScoreService,
            GradingService gradingService,
            AttemptRepository attemptRepository,
            QuizAttemptRepository quizAttemptRepository,
            TestRepository testRepository,
            PromoCodeRepository promoCodeRepository,
            PromoCodeClaimRepository promoCodeClaimRepository,
            ScoreAdjustmentRepository scoreAdjustmentRepository,
            CompetitionRepository competitionRepository,
            AcademicGroupRepository academicGroupRepository,
            @Autowired(required = false) TransactionTemplate transactionTemplate
    ) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.labScoreService = labScoreService;
        this.gradingService = gradingService;
        this.attemptRepository = attemptRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.testRepository = testRepository;
        this.promoCodeRepository = promoCodeRepository;
        this.promoCodeClaimRepository = promoCodeClaimRepository;
        this.scoreAdjustmentRepository = scoreAdjustmentRepository;
        this.competitionRepository = competitionRepository;
        this.academicGroupRepository = academicGroupRepository;
        this.transactionTemplate = transactionTemplate;
    }

    public record LeaderboardEntry(
            UUID studentId,
            String username,
            String groupName,
            int ctfScore,
            int solvedCount,
            double lPercent,
            double kPercent,
            double cPercent,
            double sScore,
            Integer recommendedGrade,
            boolean isEligible
    ) {
    }

    public SseEmitter subscribe(String scopeType, UUID scopeId) {
        SseEmitter emitter = new SseEmitter(60 * 60 * 1000L); // 1 hour timeout
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = resolveIsAdmin(auth);
        String username = resolveUsername(auth);
        UUID studentId = runInTransaction(() -> resolveStudentId(auth, username));

        Subscriber subscriber = new Subscriber(emitter, scopeType, scopeId, isAdmin, studentId, username, false);
        subscribers.add(subscriber);

        emitter.onCompletion(() -> subscribers.remove(subscriber));
        emitter.onTimeout(() -> subscribers.remove(subscriber));
        emitter.onError((e) -> subscribers.remove(subscriber));

        // Send initial state within transaction, then release DB connection before network send
        try {
            List<LeaderboardEntry> initialSnapshot = runInTransaction(() ->
                    getLeaderboardSnapshotForViewer(scopeType, scopeId, isAdmin, studentId, username));
            emitter.send(SseEmitter.event().name("INIT").data(initialSnapshot));
        } catch (IOException e) {
            subscribers.remove(subscriber);
        }

        return emitter;
    }

    public SseEmitter subscribeEvents() {
        SseEmitter emitter = new SseEmitter(60 * 60 * 1000L);
        Subscriber subscriber = new Subscriber(emitter, null, null, false, null, null, true);
        subscribers.add(subscriber);

        emitter.onCompletion(() -> subscribers.remove(subscriber));
        emitter.onTimeout(() -> subscribers.remove(subscriber));
        emitter.onError((e) -> subscribers.remove(subscriber));

        return emitter;
    }

    public void broadcastUpdate() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        scheduleBroadcast();
                    }
                }
            );
        } else {
            scheduleBroadcast();
        }
    }

    private void scheduleBroadcast() {
        if (subscribers.stream().allMatch(Subscriber::eventsOnly)) {
            return;
        }
        broadcastPending.set(true);
        if (broadcastRunning.compareAndSet(false, true)) {
            sseExecutor.execute(this::drainBroadcasts);
        }
    }

    private void drainBroadcasts() {
        try {
            while (broadcastPending.getAndSet(false)) {
                executeBroadcastSync();
            }
        } finally {
            broadcastRunning.set(false);
            if (broadcastPending.get() && broadcastRunning.compareAndSet(false, true)) {
                sseExecutor.execute(this::drainBroadcasts);
            }
        }
    }

    void executeBroadcastSync() {
        List<Subscriber> leaderboardSubscribers = subscribers.stream()
                .filter(sub -> !sub.eventsOnly())
                .toList();
        if (leaderboardSubscribers.isEmpty()) {
            return;
        }

        ProjectionContext context = runInTransaction(this::buildProjectionContext);
        Map<String, List<LeaderboardEntry>> sharedCache = new HashMap<>();
        List<Subscriber> deadSubscribers = new ArrayList<>();

        for (Subscriber sub : leaderboardSubscribers) {
            try {
                List<LeaderboardEntry> snapshot;
                if (!context.leaderboardHiddenGlobally()) {
                    String cacheKey = (sub.scopeType() == null ? "GLOBAL" : sub.scopeType().toUpperCase(Locale.ROOT))
                            + "|" + (sub.scopeId() == null ? "ALL" : sub.scopeId())
                            + "|" + sub.isAdmin();
                    snapshot = sharedCache.computeIfAbsent(cacheKey, k -> projectForViewer(
                            context,
                            sub.scopeType(),
                            sub.scopeId(),
                            sub.isAdmin(),
                            sub.studentId(),
                            sub.username()
                    ));
                } else {
                    snapshot = projectForViewer(
                            context,
                            sub.scopeType(),
                            sub.scopeId(),
                            sub.isAdmin(),
                            sub.studentId(),
                            sub.username()
                    );
                }
                sub.emitter().send(SseEmitter.event().name("UPDATE").data(snapshot));
            } catch (Exception e) {
                deadSubscribers.add(sub);
            }
        }

        if (!deadSubscribers.isEmpty()) {
            subscribers.removeAll(deadSubscribers);
        }
    }

    public void broadcastFirstBlood(String username, String challengeTitle) {
        if (subscribers.isEmpty()) {
            return;
        }
        Map<String, String> data = Map.of("username", username, "challengeTitle", challengeTitle);
        List<Subscriber> snapshotSubscribers = List.copyOf(subscribers);
        sseExecutor.execute(() -> {
            List<Subscriber> deadSubscribers = new ArrayList<>();
            for (Subscriber sub : snapshotSubscribers) {
                try {
                    sub.emitter().send(SseEmitter.event().name("FIRST_BLOOD").data(data));
                } catch (Exception e) {
                    deadSubscribers.add(sub);
                }
            }
            if (!deadSubscribers.isEmpty()) {
                subscribers.removeAll(deadSubscribers);
            }
        });
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntry> getLeaderboardSnapshot(String scopeType, UUID scopeId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = resolveIsAdmin(auth);
        String currentUsername = resolveUsername(auth);
        UUID currentStudentId = resolveStudentId(auth, currentUsername);
        return getLeaderboardSnapshotForViewer(scopeType, scopeId, isAdmin, currentStudentId, currentUsername);
    }

    private List<LeaderboardEntry> getLeaderboardSnapshotForViewer(
            String scopeType,
            UUID scopeId,
            boolean isAdmin,
            UUID viewerStudentId,
            String viewerUsername
    ) {
        ProjectionContext context = buildProjectionContext();
        return projectForViewer(context, scopeType, scopeId, isAdmin, viewerStudentId, viewerUsername);
    }

    private List<LeaderboardEntry> projectForViewer(
            ProjectionContext context,
            String scopeType,
            UUID scopeId,
            boolean isAdmin,
            UUID viewerStudentId,
            String viewerUsername
    ) {
        boolean filterGroup = scopeType != null && scopeId != null && "GROUP".equalsIgnoreCase(scopeType);
        boolean filterFlow = scopeType != null && scopeId != null && "FLOW".equalsIgnoreCase(scopeType);

        List<LeaderboardEntry> result = new ArrayList<>(context.rows().size());
        for (ComputedRow row : context.rows()) {
            if (filterGroup && !scopeId.equals(row.groupId())) {
                continue;
            }
            if (filterFlow && !scopeId.equals(row.streamId())) {
                continue;
            }
            if (row.status() == StudentStatus.OUT_OF_RATING && !isAdmin) {
                continue;
            }
            if (row.individuallyHidden() && !isAdmin) {
                continue;
            }
            boolean isCurrentUser = (viewerStudentId != null && viewerStudentId.equals(row.studentId()))
                    || (row.studentUsername() != null && viewerUsername != null
                    && row.studentUsername().equalsIgnoreCase(viewerUsername));
            if (context.leaderboardHiddenGlobally() && !isAdmin && !isCurrentUser) {
                continue;
            }
            result.add(row.entry());
        }
        return result;
    }

    private ProjectionContext buildProjectionContext() {
        List<Student> allStudents = studentRepository.findAll();
        List<QuizAttempt> allQuizzes = quizAttemptRepository.findAll();
        List<Test> allTests = testRepository.findAll();
        Map<UUID, Test> testMap = allTests.stream()
                .collect(Collectors.toMap(Test::getId, Function.identity(), (a, b) -> a));
        List<QuizQuestion> allQuestions = quizQuestionRepository != null
                ? quizQuestionRepository.findAll()
                : Collections.emptyList();
        Map<UUID, Integer> questionPointsByTestId = KtScoreCalculator.computeQuestionMaxScores(allQuestions);
        KtScoreCalculator.KtMaxProfile ktMaxProfile = KtScoreCalculator.computeMaxProfile(
                allStudents,
                allTests,
                allQuizzes,
                questionPointsByTestId
        );

        List<AcademicGroup> allGroups = academicGroupRepository.findAll();
        Map<UUID, AcademicGroup> groupMap = allGroups.stream()
                .collect(Collectors.toMap(AcademicGroup::getId, Function.identity(), (a, b) -> a));
        Map<UUID, String> groupIdToGroupName = allGroups.stream()
                .collect(Collectors.toMap(AcademicGroup::getId, AcademicGroup::getName, (a, b) -> a));

        List<User> allUsers = userRepository.findAll();
        Map<UUID, String> userIdToUsername = allUsers.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        u -> u.getUsername() != null ? u.getUsername() : u.getEmail(),
                        (a, b) -> a
                ));

        List<Competition> competitions = competitionRepository.findAll();
        Instant now = Instant.now();
        Competition activeComp = StudentScoreCalculator.selectActiveCompetition(competitions, now);
        boolean leaderboardHiddenGlobally = activeComp != null && activeComp.isLeaderboardHidden();

        List<Attempt> allAttempts = attemptRepository.findAll();
        Map<UUID, List<Attempt>> attemptsByStudent = allAttempts.stream()
                .collect(Collectors.groupingBy(Attempt::getStudentId));

        Map<UUID, List<QuizAttempt>> quizzesByStudent = allQuizzes.stream()
                .collect(Collectors.groupingBy(QuizAttempt::getStudentId));

        List<PromoCodeClaim> allClaims = promoCodeClaimRepository.findAllWithPromoCode();
        Map<UUID, List<PromoCodeClaim>> claimsByStudent = allClaims.stream()
                .collect(Collectors.groupingBy(PromoCodeClaim::getStudentId));

        List<ScoreAdjustment> allAdjustments = scoreAdjustmentRepository.findAll();
        Map<UUID, List<ScoreAdjustment>> adjustmentsByStudent = allAdjustments.stream()
                .collect(Collectors.groupingBy(ScoreAdjustment::getStudentId));

        List<UUID> allStudentIds = allStudents.stream()
                .map(Student::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, LabScoreService.StudentLabSummary> labSummaries =
                labScoreService.getLabSummariesForStudents(allStudentIds);
        if (labSummaries == null) {
            labSummaries = Collections.emptyMap();
        }
        Optional<List<GradingScale>> preloadedScales = gradingService.loadPreloadedScales();
        if (preloadedScales == null) {
            preloadedScales = Optional.empty();
        }

        Map<UUID, Instant> studentLastTxTime = new HashMap<>();
        Map<UUID, Long> studentWrongAttempts = new HashMap<>();
        List<ComputedRow> computedRows = new ArrayList<>(allStudents.size());

        for (Student student : allStudents) {
            boolean isIndividuallyHidden = activeComp != null
                    && activeComp.getHiddenStudentIds() != null
                    && activeComp.getHiddenStudentIds().contains(student.getId());

            String studentUsername = student.getUserId() != null ? userIdToUsername.get(student.getUserId()) : null;
            String displayName = resolveStudentDisplayName(student, studentUsername);

            AcademicGroup studentGroup = student.getGroupId() != null ? groupMap.get(student.getGroupId()) : null;
            UUID studentFlowId = studentGroup != null ? studentGroup.getStreamId() : null;
            String groupName = student.getGroupId() != null
                    ? groupIdToGroupName.getOrDefault(student.getGroupId(), "Без группы")
                    : "Без группы";

            boolean isDisqualified = student.getStatus() == StudentStatus.DISQUALIFIED;
            List<Attempt> studentAttempts = isDisqualified
                    ? Collections.emptyList()
                    : attemptsByStudent.getOrDefault(student.getId(), Collections.emptyList());
            List<QuizAttempt> studentQuizzes = isDisqualified
                    ? Collections.emptyList()
                    : quizzesByStudent.getOrDefault(student.getId(), Collections.emptyList());
            List<PromoCodeClaim> claims = isDisqualified
                    ? Collections.emptyList()
                    : claimsByStudent.getOrDefault(student.getId(), Collections.emptyList());
            List<ScoreAdjustment> adjustments = isDisqualified
                    ? Collections.emptyList()
                    : adjustmentsByStudent.getOrDefault(student.getId(), Collections.emptyList());

            double bLab = 0.0;
            boolean labEligible = false;
            if (!isDisqualified) {
                long wrongAttemptsCount = studentAttempts.stream().filter(a -> !a.isCorrect()).count();
                studentWrongAttempts.put(student.getId(), wrongAttemptsCount);

                Instant lastTx = null;
                for (Attempt a : studentAttempts) {
                    if (a.isCorrect() && a.getSubmittedAt() != null
                            && (lastTx == null || a.getSubmittedAt().isAfter(lastTx))) {
                        lastTx = a.getSubmittedAt();
                    }
                }
                for (PromoCodeClaim claim : claims) {
                    if (claim.getClaimedAt() != null && (lastTx == null || claim.getClaimedAt().isAfter(lastTx))) {
                        lastTx = claim.getClaimedAt();
                    }
                }
                for (ScoreAdjustment adj : adjustments) {
                    if (adj.getCreatedAt() != null && (lastTx == null || adj.getCreatedAt().isAfter(lastTx))) {
                        lastTx = adj.getCreatedAt();
                    }
                }
                if (lastTx != null) {
                    studentLastTxTime.put(student.getId(), lastTx);
                }

                LabScoreService.StudentLabSummary labSummary = labSummaries.get(student.getId());
                bLab = labSummary != null ? labSummary.finalScore() : labScoreService.getFinalLabScore(student.getId());
                labEligible = labSummary != null ? labSummary.isEligible() : labScoreService.isEligible(student.getId());
            }

            StudentScoreCalculator.StudentMetrics metrics = StudentScoreCalculator.calculateMetrics(
                    student,
                    bLab,
                    labEligible,
                    studentAttempts,
                    studentQuizzes,
                    testMap,
                    ktMaxProfile,
                    activeComp,
                    claims,
                    adjustments,
                    studentFlowId,
                    gradingService,
                    preloadedScales
            );

            computedRows.add(new ComputedRow(
                    student.getId(),
                    student.getGroupId(),
                    studentFlowId,
                    student.getStatus(),
                    isIndividuallyHidden,
                    studentUsername,
                    new LeaderboardEntry(
                            student.getId(),
                            displayName,
                            groupName,
                            metrics.ctfScore(),
                            metrics.solvedCount(),
                            metrics.lPercent(),
                            metrics.ktResult().kPercent(),
                            metrics.cPercent(),
                            metrics.sScore(),
                            metrics.recommendedGrade(),
                            metrics.isEligible()
                    )
            ));
        }

        computedRows.sort((r1, r2) -> {
            LeaderboardEntry e1 = r1.entry();
            LeaderboardEntry e2 = r2.entry();
            int scoreCompare = Integer.compare(e2.ctfScore(), e1.ctfScore());
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            Instant t1 = studentLastTxTime.get(e1.studentId());
            Instant t2 = studentLastTxTime.get(e2.studentId());
            if (t1 != null && t2 != null) {
                int timeCompare = t1.compareTo(t2);
                if (timeCompare != 0) {
                    return timeCompare;
                }
            } else if (t1 != null) {
                return -1;
            } else if (t2 != null) {
                return 1;
            }

            long w1 = studentWrongAttempts.getOrDefault(e1.studentId(), 0L);
            long w2 = studentWrongAttempts.getOrDefault(e2.studentId(), 0L);
            int wrongCompare = Long.compare(w1, w2);
            if (wrongCompare != 0) {
                return wrongCompare;
            }

            return e1.username().compareToIgnoreCase(e2.username());
        });

        return new ProjectionContext(leaderboardHiddenGlobally, computedRows);
    }

    private String resolveStudentDisplayName(Student student, String username) {
        if (username != null && !username.isBlank()) {
            return username;
        }
        String fullName = student.getFullName();
        if (fullName != null && !fullName.isBlank()
                && !"Студент".equals(fullName)
                && !"Student New".equals(fullName)) {
            return fullName;
        }
        if (student.getStudentCode() != null && !student.getStudentCode().isBlank()) {
            return student.getStudentCode();
        }
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        return "Unknown";
    }

    private boolean resolveIsAdmin(Authentication auth) {
        if (auth == null) {
            return false;
        }
        if (auth.getPrincipal() instanceof User u) {
            return u.getRole() == Role.ADMIN || u.getRole() == Role.INSTRUCTOR;
        }
        return auth.getAuthorities().stream().anyMatch(a -> {
            String r = a.getAuthority();
            return "ROLE_ADMIN".equals(r) || "ADMIN".equals(r)
                    || "ROLE_INSTRUCTOR".equals(r) || "INSTRUCTOR".equals(r);
        });
    }

    private String resolveUsername(Authentication auth) {
        if (auth == null) {
            return null;
        }
        if (auth.getPrincipal() instanceof User u) {
            return u.getUsername() != null ? u.getUsername() : u.getEmail();
        }
        return auth.getName();
    }

    private UUID resolveStudentId(Authentication auth, String username) {
        if (auth == null) {
            return null;
        }
        if (auth.getPrincipal() instanceof User u && u.getId() != null) {
            return studentRepository.findByUserId(u.getId()).map(Student::getId).orElse(null);
        }
        if (username != null && !username.isBlank()) {
            return userRepository.findByUsername(username)
                    .or(() -> userRepository.findByEmail(username))
                    .flatMap(u -> studentRepository.findByUserId(u.getId()))
                    .map(Student::getId)
                    .orElse(null);
        }
        return null;
    }

    private <T> T runInTransaction(Supplier<T> supplier) {
        if (transactionTemplate != null) {
            return transactionTemplate.execute(status -> supplier.get());
        }
        return supplier.get();
    }
}
