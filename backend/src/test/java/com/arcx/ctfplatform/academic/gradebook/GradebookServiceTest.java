package com.arcx.ctfplatform.academic.gradebook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.arcx.ctfplatform.academic.entity.AcademicGroup;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.entity.StudentStatus;
import com.arcx.ctfplatform.academic.repository.AcademicGroupRepository;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.academic.service.GradingService;
import com.arcx.ctfplatform.attempts.entity.Attempt;
import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.common.util.SimpleXlsxUtil;
import com.arcx.ctfplatform.leaderboard.service.LeaderboardService;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeClaimRepository;
import com.arcx.ctfplatform.modifiers.repository.ScoreAdjustmentRepository;
import com.arcx.ctfplatform.students.entity.lab.StudentLab;
import com.arcx.ctfplatform.students.repository.lab.LabScoreHistoryRepository;
import com.arcx.ctfplatform.students.repository.lab.LabScoreRepository;
import com.arcx.ctfplatform.students.repository.lab.StudentLabRepository;
import com.arcx.ctfplatform.students.service.lab.LabScoreService;
import com.arcx.ctfplatform.tests.repository.QuizAttemptRepository;
import com.arcx.ctfplatform.tests.repository.TestRepository;
import com.arcx.ctfplatform.users.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GradebookServiceTest {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private AcademicGroupRepository academicGroupRepository;
    @Mock
    private StudentLabRepository studentLabRepository;
    @Mock
    private LabScoreRepository labScoreRepository;
    @Mock
    private LabScoreHistoryRepository labScoreHistoryRepository;
    @Mock
    private AttemptRepository attemptRepository;
    @Mock
    private QuizAttemptRepository quizAttemptRepository;
    @Mock
    private TestRepository testRepository;
    @Mock
    private PromoCodeClaimRepository promoCodeClaimRepository;
    @Mock
    private ScoreAdjustmentRepository scoreAdjustmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LeaderboardService leaderboardService;

    private LabScoreService labScoreService;
    private GradingService gradingService;
    private GradebookService gradebookService;

    private UUID groupId;
    private AcademicGroup group;
    private Student student;

    @BeforeEach
    void setUp() {
        labScoreService = new LabScoreService(
                labScoreRepository,
                labScoreHistoryRepository,
                studentLabRepository,
                studentRepository
        );
        gradingService = new GradingService(null);
        gradebookService = new GradebookService(
                studentRepository,
                academicGroupRepository,
                studentLabRepository,
                labScoreService,
                gradingService,
                attemptRepository,
                quizAttemptRepository,
                testRepository,
                promoCodeClaimRepository,
                scoreAdjustmentRepository,
                userRepository,
                leaderboardService
        );

        groupId = UUID.randomUUID();
        group = AcademicGroup.builder().id(groupId).name("БИСТ-22-1").build();
        student = Student.builder()
                .id(UUID.randomUUID())
                .lastName("Абитов")
                .firstName("Дамир")
                .middleName("Рамилевич")
                .studentCode("STU-01")
                .academicGroup(group)
                .pairNumber(1)
                .kt1(25.0)
                .kt2(20.0)
                .kt3(25.0)
                .kt4(20.0)
                .tokenInfo("Сдал")
                .status(StudentStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Gradebook calculates exact regulation formulas for Labs, KT, TotalPoints, CTF coeff, S%, and Grade")
    void testGradebookRowCalculations() {
        List<StudentLab> labs = new ArrayList<>();
        double[] maxScores = {10, 12, 12, 15, 24, 28, 20, 22};
        for (int i = 1; i <= 8; i++) {
            labs.add(StudentLab.builder()
                    .student(student)
                    .labNumber(i)
                    .score(maxScores[i - 1])
                    .isAccepted(true)
                    .build());
        }

        Attempt ctfAttempt = Attempt.builder()
                .studentId(student.getId())
                .isCorrect(true)
                .scoreAwarded(600)
                .build();

        when(studentRepository.findAll()).thenReturn(List.of(student));
        when(academicGroupRepository.findAll()).thenReturn(List.of(group));
        when(studentLabRepository.findAllByStudentId(student.getId())).thenReturn(labs);
        when(attemptRepository.findAll()).thenReturn(List.of(ctfAttempt));
        when(quizAttemptRepository.findAll()).thenReturn(List.of());
        when(testRepository.findAll()).thenReturn(List.of());
        when(promoCodeClaimRepository.findAllWithPromoCode()).thenReturn(List.of());
        when(scoreAdjustmentRepository.findAll()).thenReturn(List.of());

        List<GradebookRowDto> rows = gradebookService.getGradebook(groupId, null);
        assertThat(rows).hasSize(1);

        GradebookRowDto row = rows.get(0);
        assertThat(row.rowNumber()).isEqualTo(1);
        assertThat(row.pairNumber()).isEqualTo(1);
        assertThat(row.fullName()).isEqualTo("Абитов Дамир Рамилевич");
        assertThat(row.totalLab()).isEqualTo(143.0);
        assertThat(row.acceptedLabsCount()).isEqualTo(8);
        assertThat(row.isEligible()).isTrue();
        assertThat(row.lPercent()).isEqualTo(100.0);

        assertThat(row.kt1()).isEqualTo(25.0);
        assertThat(row.kt2()).isEqualTo(20.0);
        assertThat(row.kt3()).isEqualTo(25.0);
        assertThat(row.kt4()).isEqualTo(20.0);
        assertThat(row.totalKt()).isEqualTo(90.0);
        assertThat(row.kPercent()).isEqualTo(90.0);
        assertThat(row.totalPoints()).isEqualTo(233.0); // 143 + 90

        assertThat(row.ctfScore()).isEqualTo(600);
        assertThat(row.ctfCoeff()).isEqualTo(0.75); // 600 / 800
        assertThat(row.cPercent()).isEqualTo(75.0);
        assertThat(row.token()).isEqualTo("Сдал");

        // S = 0.50 * 100 + 0.25 * 90 + 0.25 * 75 = 50 + 22.5 + 18.75 = 91.25
        assertThat(row.sScore()).isEqualTo(91.25);
        assertThat(row.recommendedGrade()).isEqualTo(5);
        assertThat(row.grade()).isEqualTo(5);
    }

    @Test
    @DisplayName("Gradebook sets numeric grade = 2 and recommendedGrade = null when not all 8 labs are accepted")
    void testIneligibleStudentGetsNumericGrade2() {
        List<StudentLab> labs = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            labs.add(StudentLab.builder()
                    .student(student)
                    .labNumber(i)
                    .score(15.0)
                    .isAccepted(true)
                    .build());
        }

        when(studentRepository.findAll()).thenReturn(List.of(student));
        when(academicGroupRepository.findAll()).thenReturn(List.of(group));
        when(studentLabRepository.findAllByStudentId(student.getId())).thenReturn(labs);
        when(attemptRepository.findAll()).thenReturn(List.of());
        when(quizAttemptRepository.findAll()).thenReturn(List.of());
        when(testRepository.findAll()).thenReturn(List.of());
        when(promoCodeClaimRepository.findAllWithPromoCode()).thenReturn(List.of());
        when(scoreAdjustmentRepository.findAll()).thenReturn(List.of());

        List<GradebookRowDto> rows = gradebookService.getGradebook(groupId, null);
        assertThat(rows).hasSize(1);
        GradebookRowDto row = rows.get(0);
        assertThat(row.acceptedLabsCount()).isEqualTo(7);
        assertThat(row.isEligible()).isFalse();
        assertThat(row.recommendedGrade()).isNull();
        assertThat(row.grade()).isEqualTo(2);
    }

    @Test
    @DisplayName("Export CSV and XLSX include UTF-8 BOM, all 22 Рейтинг columns, and MAX footer row")
    void testExportCsvAndXlsx() throws Exception {
        when(studentRepository.findAll()).thenReturn(List.of(student));
        when(academicGroupRepository.findAll()).thenReturn(List.of(group));
        when(studentLabRepository.findAllByStudentId(student.getId())).thenReturn(List.of());
        when(attemptRepository.findAll()).thenReturn(List.of());
        when(quizAttemptRepository.findAll()).thenReturn(List.of());
        when(testRepository.findAll()).thenReturn(List.of());
        when(promoCodeClaimRepository.findAllWithPromoCode()).thenReturn(List.of());
        when(scoreAdjustmentRepository.findAll()).thenReturn(List.of());

        byte[] csvBytes = gradebookService.exportCsv(groupId, null);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF№ п/п,Пара,ФИО студента,ЛР 1,ЛР 2,ЛР 3,ЛР 4,ЛР 5,ЛР 6,ЛР 7,ЛР 8,Общее ЛР,КТ 1,КТ 2,КТ 3,КТ 4,Общее КТ,Итог баллов,CTF,Токен,Успеваемость,Оценка");
        assertThat(csv).contains("Абитов Дамир Рамилевич");
        assertThat(csv).contains(",,MAX,10.00,12.00,12.00,15.00,24.00,28.00,20.00,22.00,143.00,25,25,25,25,100.00,243.00,1.00,,100.00,5");

        byte[] xlsxBytes = gradebookService.exportXlsx(groupId, null);
        assertThat(SimpleXlsxUtil.isXlsxFile("rating_gradebook.xlsx", xlsxBytes)).isTrue();
        List<List<String>> parsedSheet = SimpleXlsxUtil.readSheet(new ByteArrayInputStream(xlsxBytes));
        assertThat(parsedSheet).hasSize(3); // Header + 1 student + MAX row
        assertThat(parsedSheet.get(0)).containsExactlyElementsOf(GradebookService.EXPORT_HEADERS);
        assertThat(parsedSheet.get(1).get(2)).isEqualTo("Абитов Дамир Рамилевич");
        assertThat(parsedSheet.get(2).get(2)).isEqualTo("MAX");
        assertThat(parsedSheet.get(2).get(11)).isEqualTo("143.0");
        assertThat(parsedSheet.get(2).get(17)).isEqualTo("243.0");
    }

    @Test
    @DisplayName("updateStudentGradebook updates pair, KT1..4, token, examScore, and lab scores")
    void testUpdateStudentGradebook() {
        when(studentRepository.findById(student.getId())).thenReturn(Optional.of(student));
        when(studentRepository.findAll()).thenReturn(List.of(student));
        when(academicGroupRepository.findAll()).thenReturn(List.of(group));
        when(studentLabRepository.findByStudentIdAndLabNumber(student.getId(), 1)).thenReturn(Optional.empty());
        when(studentLabRepository.findAllByStudentId(student.getId())).thenReturn(List.of());
        when(attemptRepository.findAll()).thenReturn(List.of());
        when(quizAttemptRepository.findAll()).thenReturn(List.of());
        when(testRepository.findAll()).thenReturn(List.of());
        when(promoCodeClaimRepository.findAllWithPromoCode()).thenReturn(List.of());
        when(scoreAdjustmentRepository.findAll()).thenReturn(List.of());

        GradebookUpdateRequest req = new GradebookUpdateRequest(
                2,
                Map.of(1, 10.0),
                Map.of(1, true),
                24.0,
                23.0,
                22.0,
                21.0,
                "TOK-777",
                5
        );

        GradebookRowDto updated = gradebookService.updateStudentGradebook(student.getId(), req);
        assertThat(updated.pairNumber()).isEqualTo(2);
        assertThat(updated.kt1()).isEqualTo(24.0);
        assertThat(updated.kt2()).isEqualTo(23.0);
        assertThat(updated.kt3()).isEqualTo(22.0);
        assertThat(updated.kt4()).isEqualTo(21.0);
        assertThat(updated.token()).isEqualTo("TOK-777");
        assertThat(updated.examScore()).isEqualTo(5);
        verify(studentLabRepository).save(any(StudentLab.class));
    }
}
