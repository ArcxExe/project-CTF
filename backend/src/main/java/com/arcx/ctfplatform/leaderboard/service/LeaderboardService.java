package com.arcx.ctfplatform.leaderboard.service;

import com.arcx.ctfplatform.academic.entity.AcademicGroup;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.entity.StudentStatus;
import com.arcx.ctfplatform.academic.repository.AcademicGroupRepository;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.academic.service.GradingService;
import com.arcx.ctfplatform.attempts.entity.Attempt;
import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.competitions.entity.Competition;
import com.arcx.ctfplatform.competitions.entity.CompetitionStatus;
import com.arcx.ctfplatform.competitions.repository.CompetitionRepository;
import com.arcx.ctfplatform.modifiers.entity.PromoCode;
import com.arcx.ctfplatform.modifiers.entity.PromoCodeClaim;
import com.arcx.ctfplatform.modifiers.entity.ScoreAdjustment;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeClaimRepository;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeRepository;
import com.arcx.ctfplatform.modifiers.repository.ScoreAdjustmentRepository;
import com.arcx.ctfplatform.students.service.lab.LabScoreService;
import com.arcx.ctfplatform.tests.entity.QuizAttempt;
import com.arcx.ctfplatform.tests.entity.QuizAttemptStatus;
import com.arcx.ctfplatform.tests.entity.Test;
import com.arcx.ctfplatform.tests.repository.QuizAttemptRepository;
import com.arcx.ctfplatform.tests.repository.TestRepository;
import com.arcx.ctfplatform.users.entity.Role;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.users.repository.UserRepository;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
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

    private record Subscriber(
            SseEmitter emitter,
            String scopeType,
            UUID scopeId,
            boolean isAdmin,
            UUID studentId,
            String username
    ) {
    }

    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

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

        Subscriber subscriber = new Subscriber(emitter, scopeType, scopeId, isAdmin, studentId, username);
        subscribers.add(subscriber);

        emitter.onCompletion(() -> subscribers.remove(subscriber));
        emitter.onTimeout(() -> subscribers.remove(subscriber));
        emitter.onError((e) -> subscribers.remove(subscriber));

        // Send initial state within transaction
        try {
            List<LeaderboardEntry> initialSnapshot = runInTransaction(() ->
                    getLeaderboardSnapshotForViewer(scopeType, scopeId, isAdmin, studentId, username));
            emitter.send(SseEmitter.event().name("INIT").data(initialSnapshot));
        } catch (IOException e) {
            subscribers.remove(subscriber);
        }

        return emitter;
    }

    public void broadcastUpdate() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        executeBroadcast();
                    }
                }
            );
        } else {
            executeBroadcast();
        }
    }

    private void executeBroadcast() {
        if (subscribers.isEmpty()) {
            return;
        }
        List<Subscriber> deadSubscribers = new ArrayList<>();

        runInTransaction(() -> {
            for (Subscriber sub : subscribers) {
                try {
                    List<LeaderboardEntry> snapshot = getLeaderboardSnapshotForViewer(
                            sub.scopeType(),
                            sub.scopeId(),
                            sub.isAdmin(),
                            sub.studentId(),
                            sub.username()
                    );
                    sub.emitter().send(SseEmitter.event().name("UPDATE").data(snapshot));
                } catch (Exception e) {
                    deadSubscribers.add(sub);
                }
            }
            return null;
        });

        subscribers.removeAll(deadSubscribers);
    }

    public void broadcastFirstBlood(String username, String challengeTitle) {
        if (subscribers.isEmpty()) {
            return;
        }
        Map<String, String> data = Map.of("username", username, "challengeTitle", challengeTitle);
        List<Subscriber> deadSubscribers = new ArrayList<>();
        subscribers.forEach(sub -> {
            try {
                sub.emitter().send(SseEmitter.event().name("FIRST_BLOOD").data(data));
            } catch (Exception e) {
                deadSubscribers.add(sub);
            }
        });
        subscribers.removeAll(deadSubscribers);
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
        List<Student> allStudents = studentRepository.findAll();
        List<QuizAttempt> allQuizzes = quizAttemptRepository.findAll();
        List<Test> allTests = testRepository.findAll();
        Map<UUID, Test> testMap = allTests.stream()
                .collect(Collectors.toMap(Test::getId, Function.identity(), (a, b) -> a));

        List<AcademicGroup> allGroups = academicGroupRepository.findAll();
        Map<UUID, AcademicGroup> groupMap = allGroups.stream()
                .collect(Collectors.toMap(AcademicGroup::getId, Function.identity(), (a, b) -> a));
        Map<UUID, String> groupIdToGroupName = allGroups.stream()
                .collect(Collectors.toMap(AcademicGroup::getId, AcademicGroup::getName, (a, b) -> a));

        if (scopeType != null && scopeId != null) {
            if ("GROUP".equalsIgnoreCase(scopeType)) {
                allStudents = allStudents.stream().filter(s -> scopeId.equals(s.getGroupId())).toList();
            } else if ("FLOW".equalsIgnoreCase(scopeType)) {
                allStudents = allStudents.stream()
                        .filter(s -> s.getGroupId() != null)
                        .filter(s -> {
                            AcademicGroup group = groupMap.get(s.getGroupId());
                            return group != null && scopeId.equals(group.getStreamId());
                        }).toList();
            }
        }

        List<User> allUsers = userRepository.findAll();
        Map<UUID, String> userIdToUsername = allUsers.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        u -> u.getUsername() != null ? u.getUsername() : u.getEmail(),
                        (a, b) -> a
                ));

        // Determine active competition (prefer CompetitionStatus.ACTIVE)
        List<Competition> competitions = competitionRepository.findAll();
        Instant now = Instant.now();
        Competition activeComp = selectActiveCompetition(competitions, now);

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

        Map<UUID, Instant> studentLastTxTime = new HashMap<>();
        Map<UUID, Long> studentWrongAttempts = new HashMap<>();
        List<LeaderboardEntry> entries = new ArrayList<>();

        for (Student student : allStudents) {
            // Exclude OUT_OF_RATING students from non-admin leaderboards
            if (student.getStatus() == StudentStatus.OUT_OF_RATING && !isAdmin) {
                continue;
            }

            // Check if student is individually hidden
            boolean isIndividuallyHidden = activeComp != null
                    && activeComp.getHiddenStudentIds() != null
                    && activeComp.getHiddenStudentIds().contains(student.getId());
            if (isIndividuallyHidden && !isAdmin) {
                continue;
            }

            String studentUsername = userIdToUsername.get(student.getUserId());
            boolean isCurrentUser = (viewerStudentId != null && viewerStudentId.equals(student.getId()))
                    || (studentUsername != null && viewerUsername != null && studentUsername.equalsIgnoreCase(viewerUsername));

            // If leaderboard is hidden globally, students can only see their own entry
            if (leaderboardHiddenGlobally && !isAdmin && !isCurrentUser) {
                continue;
            }

            String username = userIdToUsername.getOrDefault(student.getUserId(), "Unknown");
            String groupName = student.getGroupId() != null
                    ? groupIdToGroupName.getOrDefault(student.getGroupId(), "Без группы")
                    : "Без группы";

            // Zero out scores/grade=2 for DISQUALIFIED students
            if (student.getStatus() == StudentStatus.DISQUALIFIED) {
                entries.add(new LeaderboardEntry(
                        student.getId(),
                        username,
                        groupName,
                        0,
                        0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        2,
                        false
                ));
                continue;
            }

            // --- 1. CTF SCORE CALCULATION ---
            List<Attempt> studentAttempts = attemptsByStudent.getOrDefault(student.getId(), Collections.emptyList());
            long wrongAttemptsCount = studentAttempts.stream().filter(a -> !a.isCorrect()).count();
            studentWrongAttempts.put(student.getId(), wrongAttemptsCount);
            List<Attempt> correctAttempts = studentAttempts.stream().filter(Attempt::isCorrect).toList();
            int rawCtfScore = correctAttempts.stream()
                    .mapToInt(a -> a.getScoreAwarded() == null ? 0 : a.getScoreAwarded())
                    .sum();

            List<QuizAttempt> studentQuizzes = quizzesByStudent.getOrDefault(student.getId(), Collections.emptyList());
            if (activeComp != null && activeComp.isSumTestPoints()) {
                for (QuizAttempt qa : studentQuizzes) {
                    if (qa.getStatus() == QuizAttemptStatus.COMPLETED && qa.getScore() != null) {
                        Test t = testMap.get(qa.getQuizId());
                        boolean includeQuiz = (t == null)
                                || (activeComp.getId() != null && activeComp.getId().equals(t.getCompetitionId()))
                                || (t.getCompetitionId() == null && !t.isControlPoint());
                        if (includeQuiz) {
                            rawCtfScore += Math.max(0, (int) Math.round(qa.getScore().doubleValue()));
                        }
                    }
                }
            }

            // Promo codes (batch-fetched with JOIN FETCH promoCode)
            List<PromoCodeClaim> claims = claimsByStudent.getOrDefault(student.getId(), Collections.emptyList());
            int additions = 0;
            int subtractions = 0;
            double multiplier = 1.0;
            for (PromoCodeClaim claim : claims) {
                PromoCode promo = claim.getPromoCode();
                if (promo != null && promo.getModifierType() != null) {
                    switch (promo.getModifierType()) {
                        case FIXED_ADD -> additions += promo.getValue();
                        case FIXED_SUB -> subtractions += promo.getValue();
                        case DOUBLE_COEFF, TEMP_MULTIPLIER -> {
                            int coeff = promo.getValue() <= 0 ? 2 : promo.getValue();
                            multiplier *= coeff;
                        }
                    }
                }
            }
            int ctfScore = (int) Math.round((rawCtfScore + additions) * multiplier) - subtractions;
            if (ctfScore < 0) {
                ctfScore = 0;
            }

            // Score adjustments (batch-fetched)
            List<ScoreAdjustment> adjustments = adjustmentsByStudent.getOrDefault(student.getId(), Collections.emptyList());
            ctfScore += adjustments.stream().mapToInt(ScoreAdjustment::getPoints).sum();
            if (ctfScore < 0) {
                ctfScore = 0;
            }

            // Record latest successful CTF transaction time for tie-breaking
            Instant lastTx = null;
            for (Attempt a : correctAttempts) {
                if (a.getSubmittedAt() != null && (lastTx == null || a.getSubmittedAt().isAfter(lastTx))) {
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

            int solvedCount = correctAttempts.size();

            // Component C: normative 800 raw points, capped at 100%
            double cPercent = roundToTwoDecimals(Math.max(0.0, Math.min((ctfScore / 800.0) * 100.0, 100.0)));

            // --- 2. CONTROL POINTS (K) ---
            double[] slotKt = new double[] {
                Math.max(0.0, Math.min(student.getKt1(), 25.0)),
                Math.max(0.0, Math.min(student.getKt2(), 25.0)),
                Math.max(0.0, Math.min(student.getKt3(), 25.0)),
                Math.max(0.0, Math.min(student.getKt4(), 25.0))
            };
            Map<UUID, Double> unslottedKtScores = new HashMap<>();
            for (QuizAttempt qa : studentQuizzes) {
                if (qa.getStatus() == QuizAttemptStatus.COMPLETED) {
                    Test t = testMap.get(qa.getQuizId());
                    if (t != null && t.isControlPoint()) {
                        double score = qa.getScore() != null ? Math.max(0.0, qa.getScore().doubleValue()) : 0.0;
                        double cappedScore = Math.min(score, 25.0);
                        Integer ktNum = t.getEffectiveKtNumber();
                        if (ktNum != null && ktNum >= 1 && ktNum <= 4) {
                            slotKt[ktNum - 1] = Math.max(slotKt[ktNum - 1], cappedScore);
                        } else {
                            unslottedKtScores.merge(t.getId(), cappedScore, Math::max);
                        }
                    }
                }
            }
            double bKt = slotKt[0] + slotKt[1] + slotKt[2] + slotKt[3]
                    + unslottedKtScores.values().stream().mapToDouble(Double::doubleValue).sum();
            bKt = roundToTwoDecimals(Math.min(bKt, 100.0));
            double kPercent = roundToTwoDecimals(Math.max(0.0, Math.min((bKt / 100.0) * 100.0, 100.0)));

            // --- 3. LABS (L) ---
            double bLab = labScoreService.getFinalLabScore(student.getId());
            double lPercent = roundToTwoDecimals(Math.max(0.0, Math.min((bLab / 143.0) * 100.0, 100.0)));

            // --- 4. ACADEMIC ADMISSION ELIGIBILITY ---
            boolean isEligible = labScoreService.isEligible(student.getId());

            // --- 5. OVERALL COURSE SCORE (S) ---
            double sScore = roundToTwoDecimals(0.50 * lPercent + 0.25 * kPercent + 0.25 * cPercent);

            // --- 6. RECOMMENDED ACADEMIC GRADE ---
            Integer recommendedGrade = null;
            if (isEligible) {
                Integer baseGrade = gradingService.calculateGrade(sScore, lPercent, kPercent);
                if (student.getExamScore() != null && baseGrade != null) {
                    Integer mappedExam = gradingService.mapExamScoreToGrade(student.getExamScore());
                    int examGrade = (mappedExam != null && mappedExam >= 2)
                            ? mappedExam
                            : mapExamScoreFallback(student.getExamScore());
                    recommendedGrade = Math.max(baseGrade, examGrade);
                } else {
                    recommendedGrade = baseGrade;
                }
            }

            entries.add(new LeaderboardEntry(
                    student.getId(),
                    username,
                    groupName,
                    ctfScore,
                    solvedCount,
                    lPercent,
                    kPercent,
                    cPercent,
                    sScore,
                    recommendedGrade,
                    isEligible
            ));
        }

        // --- 7. SORTING ---
        // Leaderboard sorted by descending CTF score, tie-broken by earliest last successful transaction time,
        // then by fewer wrong attempts, then by username
        entries.sort((e1, e2) -> {
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

        return entries;
    }

    private Competition selectActiveCompetition(List<Competition> competitions, Instant now) {
        if (competitions == null || competitions.isEmpty()) {
            return null;
        }
        return competitions.stream()
                .filter(c -> c.getStatus() == CompetitionStatus.ACTIVE)
                .filter(c -> (c.getStartDate() == null || !now.isBefore(c.getStartDate()))
                        && (c.getEndDate() == null || !now.isAfter(c.getEndDate())))
                .findFirst()
                .or(() -> competitions.stream()
                        .filter(c -> c.getStatus() == CompetitionStatus.ACTIVE)
                        .findFirst())
                .or(() -> competitions.stream()
                        .filter(c -> c.getStatus() == null)
                        .filter(c -> (c.getStartDate() == null || !now.isBefore(c.getStartDate()))
                                && (c.getEndDate() == null || !now.isAfter(c.getEndDate())))
                        .findFirst())
                .orElse(null);
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

    private int mapExamScoreFallback(int examScore) {
        if (examScore >= 2 && examScore <= 5) {
            return examScore;
        }
        if (examScore < 50) {
            return 2;
        } else if (examScore < 70) {
            return 3;
        } else if (examScore < 85) {
            return 4;
        }
        return 5;
    }

    private double roundToTwoDecimals(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
