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
    }
}
