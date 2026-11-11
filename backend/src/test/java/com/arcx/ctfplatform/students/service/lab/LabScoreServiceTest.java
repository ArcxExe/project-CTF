package com.arcx.ctfplatform.students.service.lab;

import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.students.entity.lab.LabScore;
import com.arcx.ctfplatform.students.entity.lab.StudentLab;
import com.arcx.ctfplatform.students.repository.lab.LabScoreHistoryRepository;
import com.arcx.ctfplatform.students.repository.lab.LabScoreRepository;
import com.arcx.ctfplatform.students.repository.lab.StudentLabRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabScoreServiceTest {

    @Mock
    private LabScoreRepository labScoreRepository;

    @Mock
    private LabScoreHistoryRepository labScoreHistoryRepository;

    @Mock
    private StudentLabRepository studentLabRepository;

    @Mock
    private StudentRepository studentRepository;

    private LabScoreService labScoreService;

    private final UUID studentId = UUID.randomUUID();
    private Student student;

    @BeforeEach
    void setUp() {
        labScoreService = new LabScoreService(
                labScoreRepository,
                labScoreHistoryRepository,
                studentLabRepository,
                studentRepository
        );
        student = Student.builder().id(studentId).studentCode("STU-100").build();
    }

    @Test
    @DisplayName("isEligible returns true when all 8 labs are accepted")
    void testAllEightLabsAcceptedIsEligible() {
        List<StudentLab> labs = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            labs.add(StudentLab.builder()
                    .student(student)
                    .labNumber(i)
                    .isAccepted(true)
                    .score(13.75)
                    .build());
        }
        when(studentLabRepository.findAllByStudentId(studentId)).thenReturn(labs);

        boolean eligible = labScoreService.isEligible(studentId);
        assertThat(eligible).isTrue();
    }

    @Test
    @DisplayName("Acceptance Criterion 4: isEligible returns false when only 7 of 8 labs are accepted")
    void testSevenOfEightLabsIsEligibleFalse() {
        List<StudentLab> labs = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            labs.add(StudentLab.builder()
                    .student(student)
                    .labNumber(i)
                    .isAccepted(true)
                    .score(13.75)
                    .build());
        }
        when(studentLabRepository.findAllByStudentId(studentId)).thenReturn(labs);

        boolean eligible = labScoreService.isEligible(studentId);
        assertThat(eligible).isFalse();
    }

    @Test
    @DisplayName("isEligible returns false when 8th lab is not accepted due to rawScore < 40%")
    void testEighthLabNotAcceptedDueToLowRawScore() {
        List<StudentLab> labs = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            labs.add(StudentLab.builder().student(student).labNumber(i).isAccepted(true).build());
        }
        labs.add(StudentLab.builder()
                .student(student)
                .labNumber(8)
                .rawScore(5.0) // 5.0 / 18.0 < 40%
                .maxRawScore(18.0)
                .defenseScore(2.0) // 2.0 / 4.0 = 50%
                .maxDefenseScore(4.0)
                .isAccepted(false)
                .build());

        when(studentLabRepository.findAllByStudentId(studentId)).thenReturn(labs);

        assertThat(labScoreService.isEligible(studentId)).isFalse();
    }

    @Test
    @DisplayName("isEligible returns false when 8th lab is not accepted due to defenseScore < 50%")
    void testEighthLabNotAcceptedDueToLowDefenseScore() {
        List<StudentLab> labs = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            labs.add(StudentLab.builder().student(student).labNumber(i).isAccepted(true).build());
        }
        labs.add(StudentLab.builder()
                .student(student)
                .labNumber(8)
                .rawScore(10.0) // 10.0 / 18.0 >= 40%
                .maxRawScore(18.0)
                .defenseScore(1.0) // 1.0 / 4.0 = 25% < 50%
                .maxDefenseScore(4.0)
                .isAccepted(false)
                .build());

        when(studentLabRepository.findAllByStudentId(studentId)).thenReturn(labs);

        assertThat(labScoreService.isEligible(studentId)).isFalse();
    }

    @Test
    @DisplayName("getFinalLabScore calculates total score from StudentLab list with 2 decimal precision")
    void testGetFinalLabScoreFromStudentLabs() {
        List<StudentLab> labs = new ArrayList<>();
        // Lab 1 with score 17.875
        labs.add(StudentLab.builder().student(student).labNumber(1).score(17.875).build());
        // Lab 2 with score 15.6625
        labs.add(StudentLab.builder().student(student).labNumber(2).score(15.6625).build());

        when(studentLabRepository.findAllByStudentId(studentId)).thenReturn(labs);

        double total = labScoreService.getFinalLabScore(studentId);
        // 17.875 + 15.6625 = 33.5375 -> rounded to 33.54
        assertThat(total).isEqualTo(33.54);
        assertThat(labScoreService.getLabScore(studentId)).isEqualTo((int) Math.round(33.54));
    }

    @Test
    @DisplayName("getFinalLabScore and isEligible fallback to LabScore entity when no StudentLab exists")
    void testFallbackToLabScore() {
        when(studentLabRepository.findAllByStudentId(studentId)).thenReturn(Collections.emptyList());
        LabScore score = LabScore.builder().student(student).score(110).isEligible(true).build();
        when(labScoreRepository.findByStudentId(studentId)).thenReturn(Optional.of(score));

        assertThat(labScoreService.getFinalLabScore(studentId)).isEqualTo(110.0);
        assertThat(labScoreService.isEligible(studentId)).isTrue();
    }

    @Test
    @DisplayName("Regulation 1.1: Lab specs catalog contains exact values for all 8 labs totalling 143.0 points")
    void testLabSpecsCatalog() {
        assertThat(LabScoreService.LAB_SPECS).hasSize(8);
        assertThat(LabScoreService.getLabSpec(1)).isEqualTo(new LabScoreService.LabSpec(1, 8.0, 2.0));
        assertThat(LabScoreService.getLabSpec(2)).isEqualTo(new LabScoreService.LabSpec(2, 10.0, 2.0));
        assertThat(LabScoreService.getLabSpec(3)).isEqualTo(new LabScoreService.LabSpec(3, 10.0, 2.0));
        assertThat(LabScoreService.getLabSpec(4)).isEqualTo(new LabScoreService.LabSpec(4, 12.0, 3.0));
        assertThat(LabScoreService.getLabSpec(5)).isEqualTo(new LabScoreService.LabSpec(5, 20.0, 4.0));
        assertThat(LabScoreService.getLabSpec(6)).isEqualTo(new LabScoreService.LabSpec(6, 24.0, 4.0));
        assertThat(LabScoreService.getLabSpec(7)).isEqualTo(new LabScoreService.LabSpec(7, 16.0, 4.0));
        assertThat(LabScoreService.getLabSpec(8)).isEqualTo(new LabScoreService.LabSpec(8, 18.0, 4.0));

        double totalRaw = LabScoreService.LAB_SPECS.values().stream().mapToDouble(LabScoreService.LabSpec::maxRawScore).sum();
        double totalDefense = LabScoreService.LAB_SPECS.values().stream().mapToDouble(LabScoreService.LabSpec::maxDefenseScore).sum();
        assertThat(totalRaw).isEqualTo(118.0);
        assertThat(totalDefense).isEqualTo(25.0);
        assertThat(totalRaw + totalDefense).isEqualTo(143.0);
    }

    @Test
    @DisplayName("Regulation 1.2: Late penalty applies to totalRaw (raw + defense)")
    void testRecordLabAcceptanceAndPenaltyOnTotalRaw() {
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(studentLabRepository.findByStudentIdAndLabNumber(studentId, 1)).thenReturn(Optional.empty());

        // Lab 1: maxRaw = 8.0, maxDefense = 2.0 (maxTotal = 10.0)
        // raw = 8.0 (>= 40%), defense = 2.0 (>= 50%), penalty = 0.85
        // totalRaw = 10.0 -> score = 10.0 * 0.85 = 8.50
        labScoreService.recordLab(studentId, 1, 8.0, 2.0, 0.85);

        verify(studentLabRepository).save(argThat(lab -> {
            return lab.isAccepted() && Math.abs(lab.getScore() - 8.50) < 0.001;
        }));
    }

    @Test
    @DisplayName("Regulation 4.8 / Test Case 5: 60% cap when defense not passed: Lab 1 raw=8, defense=0.5 -> score=6.0, accepted=false")
    void testDefenseFailed60PercentCap() {
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(studentLabRepository.findByStudentIdAndLabNumber(studentId, 1)).thenReturn(Optional.empty());

        // Lab 1: maxRaw = 8.0, maxDefense = 2.0 (maxTotal = 10.0)
        // raw = 8.0 (>= 40%), defense = 0.5 (< 50% of 2.0 = 1.0)
        // totalRaw = 8.5. Defense failed -> score capped at 60% of 10.0 = 6.00 instead of 8.50
        // accepted = false
        labScoreService.recordLab(studentId, 1, 8.0, 0.5, 1.0);

        verify(studentLabRepository).save(argThat(lab -> {
            return !lab.isAccepted() && Math.abs(lab.getScore() - 6.00) < 0.001;
        }));
    }

    @Test
    @DisplayName("StudentLab.isAccepted respects explicit false override even when raw and defense scores are high")
    void testExplicitFalseOverrideOnStudentLabIsRespected() {
        StudentLab overriddenFalse = StudentLab.builder()
                .student(student)
                .labNumber(1)
                .rawScore(8.0)
                .maxRawScore(8.0)
                .defenseScore(2.0)
                .maxDefenseScore(2.0)
                .isAccepted(false)
                .build();

        assertThat(overriddenFalse.isAccepted()).isFalse();
    }
}
