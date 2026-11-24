package com.arcx.ctfplatform.academic.service;

import com.arcx.ctfplatform.academic.repository.GradingScaleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class GradingServiceTest {

    @Mock
    private GradingScaleRepository gradingScaleRepository;

    private GradingService gradingService;

    @BeforeEach
    void setUp() {
        gradingService = new GradingService(gradingScaleRepository);
    }

    @Test
    @DisplayName("S < 50.00% results in grade 2")
    void testGradeTwo() {
        assertThat(gradingService.calculateGrade(0.0, 100.0, 100.0)).isEqualTo(2);
        assertThat(gradingService.calculateGrade(49.99, 100.0, 100.0)).isEqualTo(2);
    }

    @Test
    @DisplayName("50.00% <= S < 70.00% results in grade 3 without crossing boundary")
    void testGradeThree() {
        assertThat(gradingService.calculateGrade(50.00, 100.0, 100.0)).isEqualTo(3);
        assertThat(gradingService.calculateGrade(65.00, 100.0, 100.0)).isEqualTo(3);
        assertThat(gradingService.calculateGrade(69.99, 100.0, 100.0)).isEqualTo(3);
    }

    @Test
    @DisplayName("70.00% <= S < 85.00% results in grade 4")
    void testGradeFour() {
        assertThat(gradingService.calculateGrade(70.00, 100.0, 100.0)).isEqualTo(4);
        assertThat(gradingService.calculateGrade(73.96, 76.92, 72.00)).isEqualTo(4);
        assertThat(gradingService.calculateGrade(84.99, 100.0, 100.0)).isEqualTo(4);
    }

    @Test
    @DisplayName("Acceptance Criterion 3: S >= 85.00% with L < 60.00% results in grade 4")
    void testGradeFiveConditionFailedL() {
        assertThat(gradingService.calculateGrade(86.00, 58.00, 80.00)).isEqualTo(4);
        assertThat(gradingService.calculateGrade(85.00, 59.99, 100.00)).isEqualTo(4);
    }

    @Test
    @DisplayName("Acceptance Criterion 3: S >= 85.00% with K < 60.00% results in grade 4")
    void testGradeFiveConditionFailedK() {
        assertThat(gradingService.calculateGrade(86.00, 80.00, 58.00)).isEqualTo(4);
        assertThat(gradingService.calculateGrade(85.00, 100.00, 59.99)).isEqualTo(4);
    }

    @Test
    @DisplayName("Acceptance Criterion 3: S >= 85.00% with L >= 60.00% and K >= 60.00% results in grade 5")
    void testGradeFiveSuccess() {
        assertThat(gradingService.calculateGrade(85.00, 60.00, 60.00)).isEqualTo(5);
        assertThat(gradingService.calculateGrade(86.00, 70.00, 70.00)).isEqualTo(5);
        assertThat(gradingService.calculateGrade(100.00, 100.00, 100.00)).isEqualTo(5);
    }

    @Test
    @DisplayName("Criterion 4: recommendedGrade is null when student is not eligible")
    void testEligibilityCondition() {
        assertThat(gradingService.calculateGrade(86.00, 70.00, 70.00, false)).isNull();
        assertThat(gradingService.getRecommendedGrade(86.00, 70.00, 70.00, false)).isNull();
        assertThat(gradingService.calculateGrade(86.00, 70.00, 70.00, true)).isEqualTo(5);
        assertThat(gradingService.getRecommendedGrade(86.00, 70.00, 70.00, true)).isEqualTo(5);
    }

    @Test
    @DisplayName("Regulation 6.4 example: B_lab = 110, B_kt = 72, B_ctf = 560 -> L = 76.92%, K = 72.00%, C = 70.00% -> S = 73.96% -> Grade 4")
    void testRegulationExample6_4() {
        assertThat(gradingService.calculateGrade(73.96, 76.92, 72.00)).isEqualTo(4);
    }

    @Test
    @DisplayName("Boundary case without rounding: S = 69.99% -> 3; S = 70.00% -> 4")
    void testBoundaryWithoutRounding() {
        assertThat(gradingService.calculateGrade(69.99, 100.0, 100.0)).isEqualTo(3);
        assertThat(gradingService.calculateGrade(70.00, 100.0, 100.0)).isEqualTo(4);
    }

    @Test
    @DisplayName("Honors threshold: S = 86.00%, but K = 59.00% -> Grade lowered to 4")
    void testHonorsThresholdFailedK() {
        assertThat(gradingService.calculateGrade(86.00, 80.00, 59.00)).isEqualTo(4);
    }

    @Test
    @DisplayName("Small percentages: L = 0.70%, K = 0%, C = 0%, S = 0.35% results in grade 2")
    void testSmallPercentages() {
        assertThat(gradingService.calculateGrade(0.35, 0.70, 0.0)).isEqualTo(2);
        assertThat(gradingService.calculateGrade(0.85, 1.70, 0.0)).isEqualTo(2);
    }

    @Test
    @DisplayName("Regulation 8.1: calculateFinalGradeWithExam handles eligibility and voluntary exam")
    void testCalculateFinalGradeWithExam() {
        // Ineligible -> null
        assertThat(gradingService.calculateFinalGradeWithExam(86.0, 80.0, 80.0, false, 90)).isNull();

        // Exam grade improves course grade: course grade 3, exam 85 (grade 5) -> 5
        assertThat(gradingService.calculateFinalGradeWithExam(65.0, 60.0, 60.0, true, 85)).isEqualTo(5);

        // Exam grade does not lower course grade: course grade 4, exam 40 (grade 2) -> 4
        assertThat(gradingService.calculateFinalGradeWithExam(75.0, 70.0, 70.0, true, 40)).isEqualTo(4);

        // Exam grade does not lower course grade: course grade 4, exam 60 (grade 3) -> 4
        assertThat(gradingService.calculateFinalGradeWithExam(75.0, 70.0, 70.0, true, 60)).isEqualTo(4);

        // Exam not taken (null): returns course grade
        assertThat(gradingService.calculateFinalGradeWithExam(75.0, 70.0, 70.0, true, null)).isEqualTo(4);

        // Failing exam scores (0..49, including 2..5) map strictly on 0..100 scale -> grade 2
        assertThat(gradingService.mapExamScoreToGrade(5)).isEqualTo(2);
        assertThat(gradingService.mapExamScoreToGrade(4)).isEqualTo(2);
        assertThat(gradingService.mapExamScoreToGrade(3)).isEqualTo(2);
        assertThat(gradingService.mapExamScoreToGrade(49)).isEqualTo(2);
        assertThat(gradingService.mapExamScoreToGrade(50)).isEqualTo(3);
        assertThat(gradingService.mapExamScoreToGrade(70)).isEqualTo(4);
        assertThat(gradingService.mapExamScoreToGrade(85)).isEqualTo(5);

        // 5/100 on exam must NOT inflate course grade 3 to 5
        assertThat(gradingService.calculateFinalGradeWithExam(65.0, 60.0, 60.0, true, 5)).isEqualTo(3);
        assertThat(gradingService.calculateFinalGradeWithExam(75.0, 70.0, 70.0, true, 4)).isEqualTo(4);
    }

    @Test
    @DisplayName("Dynamic grading scale rules from repository override defaults while preserving honors threshold")
    void testDynamicGradingScaleRules() {
        com.arcx.ctfplatform.academic.entity.GradingScale rule5 = com.arcx.ctfplatform.academic.entity.GradingScale.builder()
                .minCoefficient(java.math.BigDecimal.valueOf(90.0))
                .maxCoefficient(java.math.BigDecimal.valueOf(100.0))
                .grade(5)
                .build();
        com.arcx.ctfplatform.academic.entity.GradingScale rule4 = com.arcx.ctfplatform.academic.entity.GradingScale.builder()
                .minCoefficient(java.math.BigDecimal.valueOf(75.0))
                .maxCoefficient(java.math.BigDecimal.valueOf(89.99))
                .grade(4)
                .build();
        com.arcx.ctfplatform.academic.entity.GradingScale rule3 = com.arcx.ctfplatform.academic.entity.GradingScale.builder()
                .minCoefficient(java.math.BigDecimal.valueOf(55.0))
                .maxCoefficient(java.math.BigDecimal.valueOf(74.99))
                .grade(3)
                .build();

        org.mockito.Mockito.when(gradingScaleRepository.findAll())
                .thenReturn(java.util.List.of(rule5, rule4, rule3));

        // 86.0 is < 90.0 custom rule for 5 -> grade 4
        assertThat(gradingService.calculateGrade(86.0, 80.0, 80.0)).isEqualTo(4);
        // 92.0 is >= 90.0 custom rule for 5 -> grade 5
        assertThat(gradingService.calculateGrade(92.0, 80.0, 80.0)).isEqualTo(5);
        // 92.0 with L < 60 -> lowered to 4
        assertThat(gradingService.calculateGrade(92.0, 55.0, 80.0)).isEqualTo(4);
        // Below lowest custom rule (55.0) -> grade 2
        assertThat(gradingService.calculateGrade(52.0, 80.0, 80.0)).isEqualTo(2);
    }

    @Test
    @DisplayName("Fractional scales [0.00..1.00] grade S < 1% as 2 and bridge boundary gaps (49..50, 69..70, 84..85)")
    void testFractionalScalesNoSmallPercentInflationAndNoGaps() {
        com.arcx.ctfplatform.academic.entity.GradingScale r2 = com.arcx.ctfplatform.academic.entity.GradingScale.builder()
                .minCoefficient(new java.math.BigDecimal("0.00"))
                .maxCoefficient(new java.math.BigDecimal("0.49"))
                .grade(2)
                .build();
        com.arcx.ctfplatform.academic.entity.GradingScale r3 = com.arcx.ctfplatform.academic.entity.GradingScale.builder()
                .minCoefficient(new java.math.BigDecimal("0.50"))
                .maxCoefficient(new java.math.BigDecimal("0.69"))
                .grade(3)
                .build();
        com.arcx.ctfplatform.academic.entity.GradingScale r4 = com.arcx.ctfplatform.academic.entity.GradingScale.builder()
                .minCoefficient(new java.math.BigDecimal("0.70"))
                .maxCoefficient(new java.math.BigDecimal("0.84"))
                .grade(4)
                .build();
        com.arcx.ctfplatform.academic.entity.GradingScale r5 = com.arcx.ctfplatform.academic.entity.GradingScale.builder()
                .minCoefficient(new java.math.BigDecimal("0.85"))
                .maxCoefficient(new java.math.BigDecimal("1.00"))
                .grade(5)
                .build();

        org.mockito.Mockito.when(gradingScaleRepository.findAll())
                .thenReturn(java.util.List.of(r2, r3, r4, r5));

        // S = 0.90%, L = 0.90%, K = 0.90% must be grade 2 (not 4)
        assertThat(gradingService.calculateGrade(0.90, 0.90, 0.90)).isEqualTo(2);
        // No gaps between 49..50, 69..70, 84..85
        assertThat(gradingService.calculateGrade(49.50, 80.0, 80.0)).isEqualTo(2);
        assertThat(gradingService.calculateGrade(69.50, 80.0, 80.0)).isEqualTo(3);
        assertThat(gradingService.calculateGrade(84.50, 80.0, 80.0)).isEqualTo(4);
        assertThat(gradingService.calculateGrade(85.00, 60.0, 60.0)).isEqualTo(5);
    }

    @Test
    @DisplayName("calculateGrade scopes scales by flowId (flow -> global -> default) without cross-flow leakage")
    void testFlowIdIsolationAndPriority() {
        java.util.UUID flowA = java.util.UUID.randomUUID();
        java.util.UUID flowB = java.util.UUID.randomUUID();

        com.arcx.ctfplatform.academic.entity.GradingScale flowARule5 = com.arcx.ctfplatform.academic.entity.GradingScale.builder()
                .flowId(flowA)
                .minCoefficient(java.math.BigDecimal.valueOf(95.0))
                .maxCoefficient(java.math.BigDecimal.valueOf(100.0))
                .grade(5)
                .build();
        com.arcx.ctfplatform.academic.entity.GradingScale flowARule4 = com.arcx.ctfplatform.academic.entity.GradingScale.builder()
                .flowId(flowA)
                .minCoefficient(java.math.BigDecimal.valueOf(70.0))
                .maxCoefficient(java.math.BigDecimal.valueOf(95.0))
                .grade(4)
                .build();
        com.arcx.ctfplatform.academic.entity.GradingScale globalRule5 = com.arcx.ctfplatform.academic.entity.GradingScale.builder()
                .flowId(null)
                .minCoefficient(java.math.BigDecimal.valueOf(80.0))
                .maxCoefficient(java.math.BigDecimal.valueOf(100.0))
                .grade(5)
                .build();
        com.arcx.ctfplatform.academic.entity.GradingScale globalRule4 = com.arcx.ctfplatform.academic.entity.GradingScale.builder()
                .flowId(null)
                .minCoefficient(java.math.BigDecimal.valueOf(65.0))
                .maxCoefficient(java.math.BigDecimal.valueOf(80.0))
                .grade(4)
                .build();

        org.mockito.Mockito.when(gradingScaleRepository.findAll())
                .thenReturn(java.util.List.of(flowARule5, flowARule4, globalRule5, globalRule4));

        // Flow A requires 95% for 5 -> 88% gives 4
        assertThat(gradingService.calculateGrade(88.0, 80.0, 80.0, flowA)).isEqualTo(4);
        assertThat(gradingService.calculateGrade(95.0, 80.0, 80.0, flowA)).isEqualTo(5);

        // Flow B has no custom scale -> falls back to global (80% for 5) and ignores Flow A
        assertThat(gradingService.calculateGrade(82.0, 80.0, 80.0, flowB)).isEqualTo(5);
        assertThat(gradingService.calculateGrade(82.0, 80.0, 80.0, (java.util.UUID) null)).isEqualTo(5);
    }
}
