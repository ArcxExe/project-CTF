package com.arcx.ctfplatform.leaderboard.service;

import com.arcx.ctfplatform.attempts.entity.Attempt;
import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.competitions.entity.Competition;
import com.arcx.ctfplatform.competitions.repository.CompetitionRepository;
import com.arcx.ctfplatform.modifiers.entity.PromoCode;
import com.arcx.ctfplatform.modifiers.entity.PromoCodeClaim;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeRepository;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeClaimRepository;
import com.arcx.ctfplatform.modifiers.entity.ScoreAdjustment;
import com.arcx.ctfplatform.modifiers.repository.ScoreAdjustmentRepository;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.entity.AcademicGroup;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.academic.repository.AcademicGroupRepository;
import com.arcx.ctfplatform.tests.entity.QuizAttempt;
import com.arcx.ctfplatform.tests.entity.QuizAttemptStatus;
import com.arcx.ctfplatform.tests.repository.QuizAttemptRepository;
import com.arcx.ctfplatform.tests.entity.Test;
import com.arcx.ctfplatform.tests.repository.TestRepository;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.arcx.ctfplatform.academic.service.GradingService;
import com.arcx.ctfplatform.students.service.lab.LabScoreService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
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

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

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
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));

        // Send initial state
        try {
            emitter.send(SseEmitter.event().name("INIT").data(getLeaderboardSnapshot(scopeType, scopeId)));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    public void broadcastUpdate() {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
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
        List<LeaderboardEntry> snapshot = getLeaderboardSnapshot(null, null);
        List<SseEmitter> deadEmitters = new ArrayList<>();
        
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("UPDATE").data(snapshot));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        });
        
        emitters.removeAll(deadEmitters);
    }

    public void broadcastFirstBlood(String username, String challengeTitle) {
        Map<String, String> data = Map.of("username", username, "challengeTitle", challengeTitle);
        List<SseEmitter> deadEmitters = new ArrayList<>();
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("FIRST_BLOOD").data(data));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        });
        emitters.removeAll(deadEmitters);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<LeaderboardEntry> getLeaderboardSnapshot(String scopeType, UUID scopeId) {
        List<Student> allStudents = studentRepository.findAll();
        List<QuizAttempt> allQuizzes = quizAttemptRepository.findAll();
        List<Test> allTests = testRepository.findAll();
        Map<UUID, Test> testMap = allTests.stream().collect(Collectors.toMap(Test::getId, t -> t));

        if (scopeType != null && scopeId != null) {
            if ("GROUP".equalsIgnoreCase(scopeType)) {
                allStudents = allStudents.stream().filter(s -> scopeId.equals(s.getGroupId())).toList();
            } else if ("FLOW".equalsIgnoreCase(scopeType)) {
                allStudents = allStudents.stream()
                        .filter(s -> s.getGroupId() != null)
                        .filter(s -> {
                            AcademicGroup group = academicGroupRepository.findById(s.getGroupId()).orElse(null);
                            return group != null && scopeId.equals(group.getStreamId());
                        }).toList();
            }
        }

        List<User> allUsers = userRepository.findAll();
        Map<UUID, String> userIdToUsername = allUsers.stream()
                .collect(Collectors.toMap(User::getId, u -> u.getUsername() != null ? u.getUsername() : u.getEmail()));
        List<AcademicGroup> allGroups = academicGroupRepository.findAll();
        Map<UUID, String> groupIdToGroupName = allGroups.stream()
                .collect(Collectors.toMap(AcademicGroup::getId, AcademicGroup::getName));

        // Determine active competition
        List<Competition> competitions = competitionRepository.findAll();
        Instant now = Instant.now();
        Competition activeComp = competitions.stream()
                .filter(c -> c.getStartDate() != null && c.getEndDate() != null &&
                             !now.isBefore(c.getStartDate()) && !now.isAfter(c.getEndDate()))
                .findFirst()
                .orElse(competitions.isEmpty() ? null : competitions.get(0));

        boolean leaderboardHiddenGlobally = activeComp != null && activeComp.isLeaderboardHidden();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ADMIN"));
        String currentUsername = auth != null ? auth.getName() : null;

        List<Attempt> allAttempts = attemptRepository.findAll();
        Map<UUID, List<Attempt>> attemptsByStudent = allAttempts.stream()
                .collect(Collectors.groupingBy(Attempt::getStudentId));

        Map<UUID, List<QuizAttempt>> quizzesByStudent = allQuizzes.stream()
                .collect(Collectors.groupingBy(QuizAttempt::getStudentId));

        Map<UUID, Instant> studentLastTxTime = new HashMap<>();
        Map<UUID, Long> studentWrongAttempts = new HashMap<>();
        List<LeaderboardEntry> entries = new ArrayList<>();

        for (Student student : allStudents) {
            // Check if student is individually hidden
            boolean isIndividuallyHidden = activeComp != null && activeComp.getHiddenStudentIds() != null && 
                                           activeComp.getHiddenStudentIds().contains(student.getId());
            if (isIndividuallyHidden && !isAdmin) {
                continue;
            }

            String studentUsername = userIdToUsername.get(student.getUserId());
            boolean isCurrentUser = studentUsername != null && studentUsername.equalsIgnoreCase(currentUsername);

            // If leaderboard is hidden globally, students can only see their own entry
            if (leaderboardHiddenGlobally && !isAdmin && !isCurrentUser) {
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

            // Promo codes
            List<PromoCodeClaim> claims = promoCodeClaimRepository.findAllByStudentId(student.getId());
            int additions = 0;
            int subtractions = 0;
            double multiplier = 1.0;
            for (PromoCodeClaim claim : claims) {
                PromoCode promo = claim.getPromoCode();
                if (promo != null) {
                    switch (promo.getModifierType()) {
                        case FIXED_ADD -> additions += promo.getValue();
                        case FIXED_SUB -> subtractions += promo.getValue();
                        case DOUBLE_COEFF -> multiplier *= promo.getValue();
                    }
                }
            }
            int ctfScore = (int) Math.round((rawCtfScore + additions) * multiplier) - subtractions;
            if (ctfScore < 0) {
                ctfScore = 0;
            }

            // Score adjustments
            List<ScoreAdjustment> adjustments = scoreAdjustmentRepository.findAllByStudentId(student.getId());
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
            List<QuizAttempt> studentQuizzes = quizzesByStudent.getOrDefault(student.getId(), Collections.emptyList());
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
                    int clampedExam = Math.max(2, Math.min(5, student.getExamScore()));
                    recommendedGrade = Math.max(baseGrade, clampedExam);
                } else {
                    recommendedGrade = baseGrade;
                }
            }

            String username = userIdToUsername.getOrDefault(student.getUserId(), "Unknown");
            String groupName = student.getGroupId() != null ? groupIdToGroupName.getOrDefault(student.getGroupId(), "Без группы") : "Без группы";

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

    private double roundToTwoDecimals(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
