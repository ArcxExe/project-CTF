package com.arcx.ctfplatform.students.service.lab;

import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.audit.service.AuditService;
import com.arcx.ctfplatform.students.entity.lab.LabScore;
import com.arcx.ctfplatform.students.entity.lab.StudentLab;
import com.arcx.ctfplatform.students.repository.lab.LabScoreHistoryRepository;
import com.arcx.ctfplatform.students.repository.lab.LabScoreRepository;
import com.arcx.ctfplatform.students.repository.lab.StudentLabRepository;
import com.arcx.ctfplatform.users.entity.Role;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    @DisplayName("BUG-003 & BUG-005: Summary setScore does not grant 8/8 eligibility or overwrite individual StudentLab grades")
    void testSummarySetScoreDoesNotGrantEligibilityOrOverwriteIndividualLabs() {
        StudentLab lab1 = StudentLab.builder()
                .student(student)
                .labNumber(1)
                .rawScore(8.0)
                .maxRawScore(8.0)
                .defenseScore(2.0)
                .maxDefenseScore(2.0)
                .score(10.0)
                .isAccepted(true)
                .build();

        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(studentLabRepository.findAllByStudentId(studentId)).thenReturn(List.of(lab1));
        when(labScoreRepository.findByStudentId(studentId)).thenReturn(Optional.empty());

        labScoreService.setScore(studentId, 20, "Summary entry", UUID.randomUUID());

        // Individual lab 1 must NOT be overwritten
        verify(studentLabRepository, never()).save(any());
        assertThat(lab1.getScore()).isEqualTo(10.0);
        assertThat(lab1.getRawScore()).isEqualTo(8.0);
        assertThat(lab1.getDefenseScore()).isEqualTo(2.0);
        assertThat(lab1.isAccepted()).isTrue();

        // LabScore must be saved with score=20 and isEligible=false (since only 1/8 labs accepted)
        verify(labScoreRepository).save(argThat(ls -> ls.getScore() == 20 && !ls.isEligible()));
    }

    @Test
    @DisplayName("Issue #34: Manual isEligible override is respected even with partial or full StudentLab records")
    void manualEligibilityOverrideIsRespected() {
        StudentLab lab1 = StudentLab.builder().student(student).labNumber(1).score(10.0).isAccepted(true).build();
        LabScore manualTrue = LabScore.builder()
                .student(student)
                .score(10)
                .isEligible(true)
                .manualEligibilityOverride(true)
                .build();

        when(studentLabRepository.findAllByStudentId(studentId)).thenReturn(List.of(lab1));
        when(labScoreRepository.findByStudentId(studentId)).thenReturn(Optional.of(manualTrue));
        assertThat(labScoreService.isEligible(studentId)).isTrue();

        List<StudentLab> all8 = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            all8.add(StudentLab.builder().student(student).labNumber(i).score(10.0).isAccepted(true).build());
        }
        LabScore manualFalse = LabScore.builder()
                .student(student)
                .score(80)
                .isEligible(false)
                .manualEligibilityOverride(true)
                .build();
        when(studentLabRepository.findAllByStudentId(studentId)).thenReturn(all8);
        when(labScoreRepository.findByStudentId(studentId)).thenReturn(Optional.of(manualFalse));
        assertThat(labScoreService.isEligible(studentId)).isFalse();
    }

    @Test
    @DisplayName("Issue #34: setScore without explicit isEligible preserves existing manual isEligible and overrides rounded sum")
    void setScorePreservesManualEligibilityAndOverridesRoundedSum() {
        StudentLab lab1 = StudentLab.builder().student(student).labNumber(1).score(100.4).isAccepted(true).build();
        LabScore existing = LabScore.builder()
                .student(student)
                .score(100)
                .isEligible(true)
                .manualEligibilityOverride(true)
                .manualScoreOverride(true)
                .build();

        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(labScoreRepository.findByStudentId(studentId)).thenReturn(Optional.of(existing));

        labScoreService.setScore(studentId, 100, "Manual override", UUID.randomUUID());

        verify(labScoreRepository).save(argThat(ls ->
                ls.getScore() == 100 && ls.isEligible() && ls.isManualEligibilityOverride() && ls.isManualScoreOverride()));

        when(studentLabRepository.findAllByStudentId(studentId)).thenReturn(List.of(lab1));
        assertThat(labScoreService.getFinalLabScore(studentId)).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Issue #34: recordLab rejects labNumber outside 1..8")
    void recordLabRejectsInvalidLabNumber() {
        assertThatThrownBy(() -> labScoreService.recordLab(studentId, 0, 5.0, 8.0, 1.0, 2.0, 1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid lab number");
        assertThatThrownBy(() -> labScoreService.recordLab(studentId, 9, 5.0, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid lab number");
    }

    @Test
    @DisplayName("Issue #34: importLabScores skips text header, reports line 1 fractional/negative/unknown rows, and updates valid ones")
    void importLabScoresReturnsDetailedReport() {
        String csv = "studentCode,score\n"
                + "STU-100,12.5\n"
                + "STU-100,-5\n"
                + "UNKNOWN-1,50\n"
                + "STU-100,75\n";

        when(studentRepository.findByStudentCode("STU-100")).thenReturn(Optional.of(student));
        when(studentRepository.findByStudentCode("UNKNOWN-1")).thenReturn(Optional.empty());
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(labScoreRepository.findByStudentId(studentId)).thenReturn(Optional.empty());

        LabScoreService.LabScoreImportReport report = labScoreService.importLabScores(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), UUID.randomUUID());

        assertThat(report.updatedCount()).isEqualTo(1);
        assertThat(report.skippedCount()).isEqualTo(3);
        assertThat(report.errors()).hasSize(3);
        assertThat(report.errors().get(0)).contains("12.5");
        assertThat(report.errors().get(1)).contains("-5");
        assertThat(report.errors().get(2)).contains("UNKNOWN-1");

        String line1FractionCsv = "STU-100,12.5\n";
        LabScoreService.LabScoreImportReport firstLineReport = labScoreService.importLabScores(
                new ByteArrayInputStream(line1FractionCsv.getBytes(StandardCharsets.UTF_8)), UUID.randomUUID());
        assertThat(firstLineReport.updatedCount()).isZero();
        assertThat(firstLineReport.skippedCount()).isEqualTo(1);
        assertThat(firstLineReport.errors().get(0)).contains("12.5");
    }

    @Test
    @DisplayName("Issue #34: setScore records real actor username and role in AuditService")
    void setScoreAuditsRealActor() {
        UserRepository userRepository = mock(UserRepository.class);
        AuditService auditService = mock(AuditService.class);
        ReflectionTestUtils.setField(labScoreService, "userRepository", userRepository);
        ReflectionTestUtils.setField(labScoreService, "auditService", auditService);

        UUID instructorId = UUID.randomUUID();
        User instructor = User.builder().id(instructorId).username("prof_ivanov").role(Role.INSTRUCTOR).build();

        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(labScoreRepository.findByStudentId(studentId)).thenReturn(Optional.empty());
        when(userRepository.findById(instructorId)).thenReturn(Optional.of(instructor));

        labScoreService.setScore(studentId, 55, true, "Checked", instructorId);

        verify(auditService).logEvent(
                eq(instructorId),
                eq("prof_ivanov"),
                eq("INSTRUCTOR"),
                eq("LAB_SCORE_UPDATE"),
                eq("STUDENT:" + studentId),
                eq("0"),
                eq("55"),
                isNull()
        );
    }
}
