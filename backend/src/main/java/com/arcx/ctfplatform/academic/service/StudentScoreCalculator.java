package com.arcx.ctfplatform.academic.service;

import com.arcx.ctfplatform.academic.entity.GradingScale;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.entity.StudentStatus;
import com.arcx.ctfplatform.attempts.entity.Attempt;
import com.arcx.ctfplatform.competitions.entity.Competition;
import com.arcx.ctfplatform.competitions.entity.CompetitionStatus;
import com.arcx.ctfplatform.modifiers.entity.PromoCode;
import com.arcx.ctfplatform.modifiers.entity.PromoCodeClaim;
import com.arcx.ctfplatform.modifiers.entity.ScoreAdjustment;
import com.arcx.ctfplatform.tests.entity.QuizAttempt;
import com.arcx.ctfplatform.tests.entity.QuizAttemptStatus;
import com.arcx.ctfplatform.tests.entity.Test;
import com.arcx.ctfplatform.tests.service.KtScoreCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Unified student scoring calculator shared across LeaderboardService, GradebookService,
 * AdminProgressService, and AdminAnalyticsService so all views compute identical
 * CTF, L%, K%, C%, S%, eligibility, and grade values.
 */
public final class StudentScoreCalculator {

    public static final double MAX_LAB_SCORE = 143.0;
    public static final double MAX_CTF_SCORE = 800.0;

    private StudentScoreCalculator() {
    }

    public record CtfScoreResult(
            int ctfScore,
            int solvedCount,
            double ctfCoeff,
            double cPercent
    ) {
        public static final CtfScoreResult ZERO = new CtfScoreResult(0, 0, 0.0, 0.0);
    }

    public record StudentMetrics(
            double totalLab,
            boolean isEligible,
            double lPercent,
            KtScoreCalculator.StudentKtResult ktResult,
            double totalPoints,
            int ctfScore,
            int solvedCount,
            double ctfCoeff,
            double cPercent,
            double sScore,
            Integer recommendedGrade,
            int numericGrade
    ) {
    }

    public static Competition selectActiveCompetition(List<Competition> competitions, Instant now) {
        if (competitions == null || competitions.isEmpty()) {
            return null;
        }
        Instant refTime = now != null ? now : Instant.now();
        return competitions.stream()
                .filter(c -> c.getStatus() == CompetitionStatus.ACTIVE)
                .filter(c -> (c.getStartDate() == null || !refTime.isBefore(c.getStartDate()))
                        && (c.getEndDate() == null || !refTime.isAfter(c.getEndDate())))
                .findFirst()
                .or(() -> competitions.stream()
                        .filter(c -> c.getStatus() == CompetitionStatus.ACTIVE)
                        .findFirst())
                .or(() -> competitions.stream()
                        .filter(c -> c.getStatus() == null)
                        .filter(c -> (c.getStartDate() == null || !refTime.isBefore(c.getStartDate()))
                                && (c.getEndDate() == null || !refTime.isAfter(c.getEndDate())))
                        .findFirst())
                .orElse(null);
    }

    public static CtfScoreResult calculateCtfScore(
            Student student,
            List<Attempt> attempts,
            List<QuizAttempt> quizzes,
            Map<UUID, Test> testMap,
            Competition activeComp,
            List<PromoCodeClaim> claims,
            List<ScoreAdjustment> adjustments
    ) {
        if (student != null && student.getStatus() == StudentStatus.DISQUALIFIED) {
            return CtfScoreResult.ZERO;
        }

        List<Attempt> safeAttempts = attempts != null ? attempts : Collections.emptyList();
        List<Attempt> correctAttempts = safeAttempts.stream().filter(Attempt::isCorrect).toList();
        int rawCtfScore = correctAttempts.stream()
                .mapToInt(a -> a.getScoreAwarded() == null ? 0 : a.getScoreAwarded())
                .sum();

        if (activeComp != null && activeComp.isSumTestPoints() && quizzes != null) {
            for (QuizAttempt qa : quizzes) {
                if (qa != null && qa.getStatus() == QuizAttemptStatus.COMPLETED && qa.getScore() != null) {
                    Test t = testMap != null ? testMap.get(qa.getQuizId()) : null;
                    boolean includeQuiz = (t == null)
                            || (activeComp.getId() != null && activeComp.getId().equals(t.getCompetitionId()))
                            || (t.getCompetitionId() == null && !t.isControlPoint());
                    if (includeQuiz) {
                        rawCtfScore += Math.max(0, (int) Math.round(qa.getScore()));
                    }
                }
            }
        }

        int additions = 0;
        int subtractions = 0;
        double multiplier = 1.0;
        if (claims != null) {
            for (PromoCodeClaim claim : claims) {
                PromoCode promo = claim != null ? claim.getPromoCode() : null;
                if (promo != null && promo.getModifierType() != null) {
                    int val = promo.getValue() != null ? promo.getValue() : 0;
                    switch (promo.getModifierType()) {
                        case FIXED_ADD -> additions += val;
                        case FIXED_SUB -> subtractions += val;
                        case DOUBLE_COEFF, TEMP_MULTIPLIER -> {
                            int coeff = val > 0 ? val : 2;
                            multiplier *= coeff;
                        }
                    }
                }
            }
        }

        int ctfScore = (int) Math.round((rawCtfScore + additions) * multiplier) - subtractions;
        if (ctfScore < 0) {
            ctfScore = 0;
        }

        if (adjustments != null) {
            ctfScore += adjustments.stream()
                    .mapToInt(a -> a != null && a.getPoints() != null ? a.getPoints() : 0)
                    .sum();
            if (ctfScore < 0) {
                ctfScore = 0;
            }
        }

        double ctfCoeff = roundToTwoDecimals(Math.max(0.0, Math.min(ctfScore / MAX_CTF_SCORE, 1.0)));
        double cPercent = roundToTwoDecimals(Math.max(0.0, Math.min((ctfScore / MAX_CTF_SCORE) * 100.0, 100.0)));
        return new CtfScoreResult(ctfScore, correctAttempts.size(), ctfCoeff, cPercent);
    }

    public static StudentMetrics calculateMetrics(
            Student student,
            double rawLabScore,
            boolean labEligible,
            List<Attempt> attempts,
            List<QuizAttempt> quizzes,
            Map<UUID, Test> testMap,
            KtScoreCalculator.KtMaxProfile ktMaxProfile,
            Competition activeComp,
            List<PromoCodeClaim> claims,
            List<ScoreAdjustment> adjustments,
            UUID studentFlowId,
            GradingService gradingService,
            Optional<List<GradingScale>> preloadedScales
    ) {
        if (student != null && student.getStatus() == StudentStatus.DISQUALIFIED) {
            return new StudentMetrics(
                    0.0,
                    false,
                    0.0,
                    new KtScoreCalculator.StudentKtResult(0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                    0.0,
                    0,
                    0,
                    0.0,
                    0.0,
                    0.0,
                    2,
                    2
            );
        }

        double totalLab = roundToTwoDecimals(Math.max(0.0, Math.min(rawLabScore, MAX_LAB_SCORE)));
        double lPercent = roundToTwoDecimals(Math.max(0.0, Math.min((totalLab / MAX_LAB_SCORE) * 100.0, 100.0)));

        KtScoreCalculator.StudentKtResult ktResult = KtScoreCalculator.computeStudentKt(
                student, quizzes, testMap, ktMaxProfile
        );
        double totalPoints = roundToTwoDecimals(totalLab + ktResult.totalKt());

        CtfScoreResult ctfResult = calculateCtfScore(
                student, attempts, quizzes, testMap, activeComp, claims, adjustments
        );

        double sScore = roundToTwoDecimals(
                0.50 * lPercent + 0.25 * ktResult.kPercent() + 0.25 * ctfResult.cPercent()
        );

        Integer recommendedGrade = null;
        if (labEligible && gradingService != null) {
            Integer baseGrade;
            if (preloadedScales != null && preloadedScales.isPresent()) {
                baseGrade = gradingService.calculateGrade(
                        sScore, lPercent, ktResult.kPercent(), studentFlowId, preloadedScales.get()
                );
            } else {
                baseGrade = studentFlowId != null
                        ? gradingService.calculateGrade(sScore, lPercent, ktResult.kPercent(), studentFlowId)
                        : gradingService.calculateGrade(sScore, lPercent, ktResult.kPercent());
            }

            if (student != null && student.getExamScore() != null && baseGrade != null) {
                Integer mappedExam = gradingService.mapExamScoreToGrade(student.getExamScore());
                int examGrade = (mappedExam != null && mappedExam >= 2)
                        ? mappedExam
                        : mapExamScoreFallback(student.getExamScore());
                recommendedGrade = Math.max(baseGrade, examGrade);
            } else {
                recommendedGrade = baseGrade;
            }
        }
        int numericGrade = recommendedGrade != null ? recommendedGrade : 2;

        return new StudentMetrics(
                totalLab,
                labEligible,
                lPercent,
                ktResult,
                totalPoints,
                ctfResult.ctfScore(),
                ctfResult.solvedCount(),
                ctfResult.ctfCoeff(),
                ctfResult.cPercent(),
                sScore,
                recommendedGrade,
                numericGrade
        );
    }

    public static int mapExamScoreFallback(int examScore) {
        if (examScore < 50) {
            return 2;
        } else if (examScore < 70) {
            return 3;
        } else if (examScore < 85) {
            return 4;
        }
        return 5;
    }

    public static double roundToTwoDecimals(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
