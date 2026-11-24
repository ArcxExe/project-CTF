package com.arcx.ctfplatform.tests.service;

import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.tests.entity.QuizAttempt;
import com.arcx.ctfplatform.tests.entity.QuizAttemptStatus;
import com.arcx.ctfplatform.tests.entity.QuizQuestion;
import com.arcx.ctfplatform.tests.entity.Test;
import com.arcx.ctfplatform.tests.entity.TestStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class KtScoreCalculator {

    private static final double DEFAULT_MANUAL_KT_MAX = 25.0;

    private KtScoreCalculator() {
    }

    public record KtMaxProfile(
            double kt1Max,
            double kt2Max,
            double kt3Max,
            double kt4Max,
            double totalKtMax,
            Map<UUID, Double> unslottedMaxByTestId
    ) {
    }

    public record StudentKtResult(
            double kt1,
            double kt2,
            double kt3,
            double kt4,
            double totalKt,
            double kPercent
    ) {
    }

    public static Map<UUID, Integer> computeQuestionMaxScores(Collection<QuizQuestion> questions) {
        if (questions == null || questions.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, Integer> result = new HashMap<>();
        for (QuizQuestion q : questions) {
            if (q == null || q.getTestId() == null) {
                continue;
            }
            int pts = q.getPoints() != null ? Math.max(0, q.getPoints()) : 0;
            result.merge(q.getTestId(), pts, Integer::sum);
        }
        return result;
    }

    public static Map<UUID, Integer> computeQuestionCounts(Collection<QuizQuestion> questions) {
        if (questions == null || questions.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, Integer> result = new HashMap<>();
        for (QuizQuestion q : questions) {
            if (q == null || q.getTestId() == null) {
                continue;
            }
            result.merge(q.getTestId(), 1, Integer::sum);
        }
        return result;
    }

    public static KtMaxProfile computeMaxProfile(
            List<Student> students,
            List<Test> allTests,
            List<QuizAttempt> allQuizAttempts,
            Map<UUID, Integer> questionPointsByTestId
    ) {
        List<Student> safeStudents = students != null ? students : Collections.emptyList();
        List<Test> safeTests = allTests != null ? allTests : Collections.emptyList();
        List<QuizAttempt> safeAttempts = allQuizAttempts != null ? allQuizAttempts : Collections.emptyList();
        Map<UUID, Integer> qPoints = questionPointsByTestId != null ? questionPointsByTestId : Collections.emptyMap();

        Set<UUID> testsWithCompletedAttempts = new HashSet<>();
        Map<UUID, Double> maxAttemptScoreByTest = new HashMap<>();
        for (QuizAttempt qa : safeAttempts) {
            if (qa != null && qa.getStatus() == QuizAttemptStatus.COMPLETED && qa.getQuizId() != null) {
                testsWithCompletedAttempts.add(qa.getQuizId());
                double sc = qa.getScore() != null ? Math.max(0.0, qa.getScore()) : 0.0;
                maxAttemptScoreByTest.merge(qa.getQuizId(), sc, Math::max);
            }
        }

        double[] maxManual = new double[4];
        for (Student s : safeStudents) {
            if (s == null) {
                continue;
            }
            maxManual[0] = Math.max(maxManual[0], Math.max(0.0, s.getKt1()));
            maxManual[1] = Math.max(maxManual[1], Math.max(0.0, s.getKt2()));
            maxManual[2] = Math.max(maxManual[2], Math.max(0.0, s.getKt3()));
            maxManual[3] = Math.max(maxManual[3], Math.max(0.0, s.getKt4()));
        }

        List<Test> activeKtTests = safeTests.stream()
                .filter(t -> t != null && t.isControlPoint())
                .filter(t -> t.getStatus() == TestStatus.PUBLISHED
                        || t.getStatus() == null
                        || testsWithCompletedAttempts.contains(t.getId()))
                .toList();

        boolean hasKtTestWithQuestions = activeKtTests.stream()
                .anyMatch(t -> qPoints.getOrDefault(t.getId(), 0) > 0);

        double[] slotMax = new double[4];
        Map<UUID, Double> unslottedMax = new HashMap<>();

        if (hasKtTestWithQuestions) {
            for (Test t : activeKtTests) {
                int qSum = qPoints.getOrDefault(t.getId(), 0);
                if (qSum <= 0) {
                    continue;
                }
                Integer ktNum = t.getEffectiveKtNumber();
                if (ktNum != null && ktNum >= 1 && ktNum <= 4) {
                    slotMax[ktNum - 1] = Math.max(slotMax[ktNum - 1], (double) qSum);
                } else {
                    unslottedMax.merge(t.getId(), (double) qSum, Math::max);
                }
            }
            for (int i = 0; i < 4; i++) {
                if (slotMax[i] == 0.0 && maxManual[i] > 0.0) {
                    slotMax[i] = Math.max(DEFAULT_MANUAL_KT_MAX, maxManual[i]);
                }
            }
        } else {
            double[] maxAttemptBySlot = new double[4];
            for (Test t : activeKtTests) {
                double maxAttempt = maxAttemptScoreByTest.getOrDefault(t.getId(), 0.0);
                Integer ktNum = t.getEffectiveKtNumber();
                if (ktNum != null && ktNum >= 1 && ktNum <= 4) {
                    maxAttemptBySlot[ktNum - 1] = Math.max(maxAttemptBySlot[ktNum - 1], maxAttempt);
                } else if (maxAttempt > 0.0) {
                    unslottedMax.merge(t.getId(), Math.max(DEFAULT_MANUAL_KT_MAX, maxAttempt), Math::max);
                }
            }
            for (int i = 0; i < 4; i++) {
                slotMax[i] = Math.max(DEFAULT_MANUAL_KT_MAX, Math.max(maxManual[i], maxAttemptBySlot[i]));
            }
        }

        double totalKtMax = slotMax[0] + slotMax[1] + slotMax[2] + slotMax[3]
                + unslottedMax.values().stream().mapToDouble(Double::doubleValue).sum();

        return new KtMaxProfile(
                roundToTwoDecimals(slotMax[0]),
                roundToTwoDecimals(slotMax[1]),
                roundToTwoDecimals(slotMax[2]),
                roundToTwoDecimals(slotMax[3]),
                roundToTwoDecimals(totalKtMax),
                unslottedMax
        );
    }

    public static StudentKtResult computeStudentKt(
            Student student,
            List<QuizAttempt> studentQuizzes,
            Map<UUID, Test> testMap,
            KtMaxProfile maxProfile
    ) {
        double[] slotKt = new double[] {
                roundToTwoDecimals(Math.max(0.0, student.getKt1())),
                roundToTwoDecimals(Math.max(0.0, student.getKt2())),
                roundToTwoDecimals(Math.max(0.0, student.getKt3())),
                roundToTwoDecimals(Math.max(0.0, student.getKt4()))
        };
        Map<UUID, Double> unslottedKt = new HashMap<>();

        if (studentQuizzes != null && testMap != null) {
            for (QuizAttempt qa : studentQuizzes) {
                if (qa != null && qa.getStatus() == QuizAttemptStatus.COMPLETED) {
                    Test t = testMap.get(qa.getQuizId());
                    if (t != null && t.isControlPoint()) {
                        double qScore = qa.getScore() != null
                                ? roundToTwoDecimals(Math.max(0.0, qa.getScore()))
                                : 0.0;
                        Integer ktNum = t.getEffectiveKtNumber();
                        if (ktNum != null && ktNum >= 1 && ktNum <= 4) {
                            slotKt[ktNum - 1] = Math.max(slotKt[ktNum - 1], qScore);
                        } else {
                            unslottedKt.merge(t.getId(), qScore, Math::max);
                        }
                    }
                }
            }
        }

        double totalKt = roundToTwoDecimals(
                slotKt[0] + slotKt[1] + slotKt[2] + slotKt[3]
                        + unslottedKt.values().stream().mapToDouble(Double::doubleValue).sum()
        );
        double maxTotalKt = maxProfile != null ? maxProfile.totalKtMax() : 0.0;
        double kPercent = maxTotalKt > 0.0
                ? roundToTwoDecimals(Math.max(0.0, Math.min((totalKt / maxTotalKt) * 100.0, 100.0)))
                : 0.0;

        return new StudentKtResult(
                slotKt[0],
                slotKt[1],
                slotKt[2],
                slotKt[3],
                totalKt,
                kPercent
        );
    }

    public static double roundToTwoDecimals(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
