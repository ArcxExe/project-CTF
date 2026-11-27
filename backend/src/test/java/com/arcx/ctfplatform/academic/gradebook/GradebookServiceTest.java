package com.arcx.ctfplatform.academic.gradebook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import com.arcx.ctfplatform.tests.entity.QuizAttempt;
import com.arcx.ctfplatform.tests.entity.QuizAttemptStatus;
import com.arcx.ctfplatform.tests.entity.QuizQuestion;
import com.arcx.ctfplatform.tests.entity.TestStatus;
import com.arcx.ctfplatform.tests.entity.TestType;
import com.arcx.ctfplatform.tests.repository.QuizAttemptRepository;
import com.arcx.ctfplatform.tests.repository.QuizQuestionRepository;
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
    private QuizQuestionRepository quizQuestionRepository;
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
        when(studentLabRepository.findAllByStudentIdIn(List.of(student.getId()))).thenReturn(labs);
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
        when(studentLabRepository.findAllByStudentIdIn(List.of(student.getId()))).thenReturn(labs);
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
        when(studentLabRepository.findAllByStudentIdIn(List.of(student.getId()))).thenReturn(List.of());
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
        when(academicGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(studentLabRepository.findByStudentIdAndLabNumber(student.getId(), 1)).thenReturn(Optional.empty());
        when(studentLabRepository.findAllByStudentId(student.getId())).thenReturn(List.of());
        when(attemptRepository.findAllByStudentId(student.getId())).thenReturn(List.of());
        when(quizAttemptRepository.findAllByStudentId(student.getId())).thenReturn(List.of());
        when(promoCodeClaimRepository.findAllByStudentIdWithPromoCode(student.getId())).thenReturn(List.of());
        when(scoreAdjustmentRepository.findAllByStudentId(student.getId())).thenReturn(List.of());

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

    @Test
    @DisplayName("BUG-003 & BUG-005: Summary LabScore with 1 accepted StudentLab keeps acceptedLabsCount=1 and Lab 1=10.0")
    void testSummaryLabScoreWithOneAcceptedLabPreservesLabAndEligibility() {
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
        com.arcx.ctfplatform.students.entity.lab.LabScore summaryScore =
                com.arcx.ctfplatform.students.entity.lab.LabScore.builder()
                        .student(student)
                        .score(20)
                        .isEligible(false)
                        .build();

        when(studentRepository.findAll()).thenReturn(List.of(student));
        when(academicGroupRepository.findAll()).thenReturn(List.of(group));
        when(studentLabRepository.findAllByStudentIdIn(List.of(student.getId()))).thenReturn(List.of(lab1));
        when(labScoreRepository.findAllByStudentIdIn(List.of(student.getId()))).thenReturn(List.of(summaryScore));
        when(attemptRepository.findAll()).thenReturn(List.of());
        when(quizAttemptRepository.findAll()).thenReturn(List.of());
        when(testRepository.findAll()).thenReturn(List.of());
        when(promoCodeClaimRepository.findAllWithPromoCode()).thenReturn(List.of());
        when(scoreAdjustmentRepository.findAll()).thenReturn(List.of());

        List<GradebookRowDto> rows = gradebookService.getGradebook(groupId, null);
        assertThat(rows).hasSize(1);
        GradebookRowDto row = rows.get(0);
        assertThat(row.labs().get(1).score()).isEqualTo(10.0);
        assertThat(row.labs().get(1).rawScore()).isEqualTo(8.0);
        assertThat(row.labs().get(1).defenseScore()).isEqualTo(2.0);
        assertThat(row.labs().get(1).accepted()).isTrue();
        assertThat(row.totalLab()).isEqualTo(20.0);
        assertThat(row.acceptedLabsCount()).isEqualTo(1);
        assertThat(row.isEligible()).isFalse();
        assertThat(row.grade()).isEqualTo(2);
    }

    @Test
    @DisplayName("Issue #24: getMyGradebookRow loads only single student data without findAll table scans")
    void getMyGradebookRowUsesSingleStudentQueriesWithoutFullScans() {
        UUID userId = UUID.randomUUID();
        student.setUserId(userId);
        com.arcx.ctfplatform.users.entity.User user = com.arcx.ctfplatform.users.entity.User.builder()
                .id(userId)
                .username("damir")
                .build();

        when(userRepository.findByUsername("damir")).thenReturn(Optional.of(user));
        when(studentRepository.findByUserId(userId)).thenReturn(Optional.of(student));
        when(academicGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(attemptRepository.findAllByStudentId(student.getId())).thenReturn(List.of());
        when(quizAttemptRepository.findAllByStudentId(student.getId())).thenReturn(List.of());
        when(promoCodeClaimRepository.findAllByStudentIdWithPromoCode(student.getId())).thenReturn(List.of());
        when(scoreAdjustmentRepository.findAllByStudentId(student.getId())).thenReturn(List.of());
        when(studentLabRepository.findAllByStudentId(student.getId())).thenReturn(List.of());

        Optional<GradebookRowDto> rowOpt = gradebookService.getMyGradebookRow("damir");

        assertThat(rowOpt).isPresent();
        assertThat(rowOpt.get().studentId()).isEqualTo(student.getId());
        verify(studentRepository, never()).findAll();
        verify(attemptRepository, never()).findAll();
        verify(quizAttemptRepository, never()).findAll();
        verify(academicGroupRepository, never()).findAll();
    }

    @Test
    @DisplayName("KT scores are not capped at 25 and K% is computed from actual published KT question max points")
    void testUncappedKtScoresAndDynamicMaxKt() {
        gradebookService.setQuizQuestionRepository(quizQuestionRepository);

        Student freshStudent = Student.builder()
                .id(UUID.randomUUID())
                .lastName("Тестов")
                .firstName("Иван")
                .studentCode("STU-KT")
                .academicGroup(group)
                .status(StudentStatus.ACTIVE)
                .build();

        UUID kt1Id = UUID.randomUUID();
        UUID kt2Id = UUID.randomUUID();
        UUID kt3Id = UUID.randomUUID();
        var kt1 = com.arcx.ctfplatform.tests.entity.Test.builder()
                .id(kt1Id).title("КТ 1").type(TestType.CONTROL_POINT).ktNumber(1).status(TestStatus.PUBLISHED).build();
        var kt2 = com.arcx.ctfplatform.tests.entity.Test.builder()
                .id(kt2Id).title("КТ 2").type(TestType.CONTROL_POINT).ktNumber(2).status(TestStatus.PUBLISHED).build();
        var kt3 = com.arcx.ctfplatform.tests.entity.Test.builder()
                .id(kt3Id).title("КТ 3").type(TestType.CONTROL_POINT).ktNumber(3).status(TestStatus.PUBLISHED).build();

        List<QuizQuestion> questions = List.of(
                QuizQuestion.builder().id(UUID.randomUUID()).testId(kt1Id).points(40).ordering(1).build(),
                QuizQuestion.builder().id(UUID.randomUUID()).testId(kt2Id).points(20).ordering(1).build(),
                QuizQuestion.builder().id(UUID.randomUUID()).testId(kt3Id).points(40).ordering(1).build()
        );

        List<QuizAttempt> quizAttempts = List.of(
                QuizAttempt.builder().studentId(freshStudent.getId()).quizId(kt1Id).status(QuizAttemptStatus.COMPLETED).score(40.0).build(),
                QuizAttempt.builder().studentId(freshStudent.getId()).quizId(kt3Id).status(QuizAttemptStatus.COMPLETED).score(10.0).build()
        );

        when(studentRepository.findAll()).thenReturn(List.of(freshStudent));
        when(academicGroupRepository.findAll()).thenReturn(List.of(group));
        when(studentLabRepository.findAllByStudentIdIn(List.of(freshStudent.getId()))).thenReturn(List.of());
        when(attemptRepository.findAll()).thenReturn(List.of());
        when(quizAttemptRepository.findAll()).thenReturn(quizAttempts);
        when(testRepository.findAll()).thenReturn(List.of(kt1, kt2, kt3));
        when(quizQuestionRepository.findAll()).thenReturn(questions);
        when(promoCodeClaimRepository.findAllWithPromoCode()).thenReturn(List.of());
        when(scoreAdjustmentRepository.findAll()).thenReturn(List.of());

        List<GradebookRowDto> rows = gradebookService.getGradebook(groupId, null);
        assertThat(rows).hasSize(1);
        GradebookRowDto row = rows.get(0);

        assertThat(row.kt1()).isEqualTo(40.0);
        assertThat(row.kt2()).isEqualTo(0.0);
        assertThat(row.kt3()).isEqualTo(10.0);
        assertThat(row.kt4()).isEqualTo(0.0);
        assertThat(row.totalKt()).isEqualTo(50.0);
        assertThat(row.maxKt1()).isEqualTo(40.0);
        assertThat(row.maxKt2()).isEqualTo(20.0);
        assertThat(row.maxKt3()).isEqualTo(40.0);
        assertThat(row.maxKt4()).isEqualTo(0.0);
        assertThat(row.maxTotalKt()).isEqualTo(100.0);
        assertThat(row.maxTotalPoints()).isEqualTo(243.0);
        assertThat(row.kPercent()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("Issue #28: DISQUALIFIED student has zeroed labs, KT, CTF, S%, isEligible=false, and grade=2")
    void testDisqualifiedStudentZeroedInGradebook() {
        student.setStatus(StudentStatus.DISQUALIFIED);
        student.setExamScore(95);

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
                .scoreAwarded(800)
                .build();

        when(studentRepository.findAll()).thenReturn(List.of(student));
        when(academicGroupRepository.findAll()).thenReturn(List.of(group));
        when(studentLabRepository.findAllByStudentIdIn(List.of(student.getId()))).thenReturn(labs);
        when(attemptRepository.findAll()).thenReturn(List.of(ctfAttempt));
        when(quizAttemptRepository.findAll()).thenReturn(List.of());
        when(testRepository.findAll()).thenReturn(List.of());
        when(promoCodeClaimRepository.findAllWithPromoCode()).thenReturn(List.of());
        when(scoreAdjustmentRepository.findAll()).thenReturn(List.of());

        List<GradebookRowDto> rows = gradebookService.getGradebook(groupId, null);
        assertThat(rows).hasSize(1);
        GradebookRowDto row = rows.get(0);

        assertThat(row.status()).isEqualTo("disqualified");
        assertThat(row.totalLab()).isZero();
        assertThat(row.acceptedLabsCount()).isZero();
        assertThat(row.isEligible()).isFalse();
        assertThat(row.lPercent()).isZero();
        assertThat(row.kt1()).isZero();
        assertThat(row.kt2()).isZero();
        assertThat(row.kt3()).isZero();
        assertThat(row.kt4()).isZero();
        assertThat(row.totalKt()).isZero();
        assertThat(row.kPercent()).isZero();
        assertThat(row.totalPoints()).isZero();
        assertThat(row.ctfScore()).isZero();
        assertThat(row.ctfCoeff()).isZero();
        assertThat(row.cPercent()).isZero();
        assertThat(row.sScore()).isZero();
        assertThat(row.recommendedGrade()).isEqualTo(2);
        assertThat(row.grade()).isEqualTo(2);
        assertThat(row.labs().get(1).score()).isZero();
        assertThat(row.labs().get(1).accepted()).isFalse();
    }

    @Test
    @DisplayName("Issue #28: Gradebook respects active competition sumTestPoints flag identically to Leaderboard")
    void testGradebookIncludesQuizPointsWhenSumTestPointsEnabled() {
        com.arcx.ctfplatform.competitions.repository.CompetitionRepository competitionRepository =
                org.mockito.Mockito.mock(com.arcx.ctfplatform.competitions.repository.CompetitionRepository.class);
        gradebookService.setCompetitionRepository(competitionRepository);

        UUID compId = UUID.randomUUID();
        com.arcx.ctfplatform.competitions.entity.Competition activeComp =
                com.arcx.ctfplatform.competitions.entity.Competition.builder()
                        .id(compId)
                        .status(com.arcx.ctfplatform.competitions.entity.CompetitionStatus.ACTIVE)
                        .sumTestPoints(true)
                        .build();
        when(competitionRepository.findAll()).thenReturn(List.of(activeComp));

        UUID lectureTestId = UUID.randomUUID();
        var lectureTest = com.arcx.ctfplatform.tests.entity.Test.builder()
                .id(lectureTestId)
                .competitionId(compId)
                .type(TestType.LECTURE)
                .build();
        QuizAttempt qa = QuizAttempt.builder()
                .studentId(student.getId())
                .quizId(lectureTestId)
                .status(QuizAttemptStatus.COMPLETED)
                .score(120.0)
                .build();
        Attempt attempt = Attempt.builder()
                .studentId(student.getId())
                .isCorrect(true)
                .scoreAwarded(280)
                .build();

        when(studentRepository.findAll()).thenReturn(List.of(student));
        when(academicGroupRepository.findAll()).thenReturn(List.of(group));
        when(studentLabRepository.findAllByStudentIdIn(List.of(student.getId()))).thenReturn(List.of());
        when(attemptRepository.findAll()).thenReturn(List.of(attempt));
        when(quizAttemptRepository.findAll()).thenReturn(List.of(qa));
        when(testRepository.findAll()).thenReturn(List.of(lectureTest));
        when(promoCodeClaimRepository.findAllWithPromoCode()).thenReturn(List.of());
        when(scoreAdjustmentRepository.findAll()).thenReturn(List.of());

        List<GradebookRowDto> rows = gradebookService.getGradebook(groupId, null);
        assertThat(rows).hasSize(1);
        // 280 task + 120 quiz = 400 -> 400 / 800 = 0.50 (50.0%)
        assertThat(rows.get(0).ctfScore()).isEqualTo(400);
        assertThat(rows.get(0).ctfCoeff()).isEqualTo(0.50);
        assertThat(rows.get(0).cPercent()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("Issue #28: updateLabDetail preserves penaltyCoefficient = 0.0 and buildRowForStudent returns 0.0")
    void testPenaltyCoefficientZeroIsPreserved() {
        StudentLab savedLab = StudentLab.builder()
                .student(student)
                .labNumber(1)
                .rawScore(8.0)
                .maxRawScore(8.0)
                .defenseScore(2.0)
                .maxDefenseScore(2.0)
                .penaltyCoefficient(0.0)
                .score(0.0)
                .isAccepted(true)
                .build();

        when(studentRepository.findById(student.getId())).thenReturn(Optional.of(student));
        when(academicGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(studentLabRepository.findByStudentIdAndLabNumber(student.getId(), 1)).thenReturn(Optional.empty());
        when(studentLabRepository.save(any(StudentLab.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studentLabRepository.findAllByStudentId(student.getId())).thenReturn(List.of(savedLab));
        when(attemptRepository.findAllByStudentId(student.getId())).thenReturn(List.of());
        when(quizAttemptRepository.findAllByStudentId(student.getId())).thenReturn(List.of());
        when(promoCodeClaimRepository.findAllByStudentIdWithPromoCode(student.getId())).thenReturn(List.of());
        when(scoreAdjustmentRepository.findAllByStudentId(student.getId())).thenReturn(List.of());

        LabDetailUpdateRequest req = new LabDetailUpdateRequest(student.getId(), 1, 8.0, 2.0, 0.0, null);
        GradebookRowDto updated = gradebookService.updateLabDetail(req);

        org.mockito.ArgumentCaptor<StudentLab> labCaptor = org.mockito.ArgumentCaptor.forClass(StudentLab.class);
        verify(studentLabRepository).save(labCaptor.capture());
        assertThat(labCaptor.getValue().getPenaltyCoefficient()).isEqualTo(0.0);
        assertThat(labCaptor.getValue().getScore()).isEqualTo(0.0);
        assertThat(updated.labs().get(1).penaltyCoefficient()).isEqualTo(0.0);
        assertThat(updated.labs().get(1).score()).isEqualTo(0.0);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> gradebookService.updateLabDetail(
                        new LabDetailUpdateRequest(student.getId(), 1, 8.0, 2.0, -0.1, null)
                )
        );
    }

    @Test
    @DisplayName("Issue #28: examScore can be explicitly cleared via clearExamScore or JSON null, while omitted keeps value")
    void testExamScoreClearAndJsonDeserialization() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        GradebookUpdateRequest nullInJson = mapper.readValue("{\"examScore\": null}", GradebookUpdateRequest.class);
        assertThat(nullInJson.examScore()).isNull();
        assertThat(nullInJson.clearExamScore()).isTrue();

        GradebookUpdateRequest omittedInJson = mapper.readValue("{\"kt1\": 18.5}", GradebookUpdateRequest.class);
        assertThat(omittedInJson.examScore()).isNull();
        assertThat(omittedInJson.clearExamScore()).isNull();
        assertThat(omittedInJson.kt1()).isEqualTo(18.5);

        student.setExamScore(88);
        when(studentRepository.findById(student.getId())).thenReturn(Optional.of(student));
        when(academicGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(studentLabRepository.findAllByStudentId(student.getId())).thenReturn(List.of());
        when(attemptRepository.findAllByStudentId(student.getId())).thenReturn(List.of());
        when(quizAttemptRepository.findAllByStudentId(student.getId())).thenReturn(List.of());
        when(promoCodeClaimRepository.findAllByStudentIdWithPromoCode(student.getId())).thenReturn(List.of());
        when(scoreAdjustmentRepository.findAllByStudentId(student.getId())).thenReturn(List.of());

        // Omitted examScore preserves 88
        GradebookRowDto kept = gradebookService.updateStudentGradebook(student.getId(), omittedInJson);
        assertThat(kept.examScore()).isEqualTo(88);

        // Explicit clear sets examScore to null
        GradebookRowDto cleared = gradebookService.updateStudentGradebook(student.getId(), nullInJson);
        assertThat(cleared.examScore()).isNull();
        assertThat(student.getExamScore()).isNull();
    }

    @Test
    @DisplayName("Issue #28: Direct lab score edit recalculates rawScore/defenseScore and accepted; unchanged score preserves breakdown")
    void testDirectLabScoreEditRecalculatesBreakdownAndAccepted() {
        StudentLab failedLab1 = StudentLab.builder()
                .student(student)
                .labNumber(1)
                .rawScore(1.6)
                .maxRawScore(8.0)
                .defenseScore(0.4)
                .maxDefenseScore(2.0)
                .penaltyCoefficient(1.0)
                .score(2.0)
                .isAccepted(false)
                .build();
        StudentLab detailedLab2 = StudentLab.builder()
                .student(student)
                .labNumber(2)
                .rawScore(10.0)
                .maxRawScore(10.0)
                .defenseScore(2.0)
                .maxDefenseScore(2.0)
                .penaltyCoefficient(0.85)
                .score(10.2)
                .isAccepted(true)
                .build();

        when(studentRepository.findById(student.getId())).thenReturn(Optional.of(student));
        when(academicGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(studentLabRepository.findByStudentIdAndLabNumber(student.getId(), 1)).thenReturn(Optional.of(failedLab1));
        when(studentLabRepository.findByStudentIdAndLabNumber(student.getId(), 2)).thenReturn(Optional.of(detailedLab2));
        when(studentLabRepository.findAllByStudentId(student.getId())).thenReturn(List.of(failedLab1, detailedLab2));
        when(attemptRepository.findAllByStudentId(student.getId())).thenReturn(List.of());
        when(quizAttemptRepository.findAllByStudentId(student.getId())).thenReturn(List.of());
        when(promoCodeClaimRepository.findAllByStudentIdWithPromoCode(student.getId())).thenReturn(List.of());
        when(scoreAdjustmentRepository.findAllByStudentId(student.getId())).thenReturn(List.of());

        GradebookUpdateRequest req = new GradebookUpdateRequest(
                null,
                Map.of(1, 9.0, 2, 10.2),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        gradebookService.updateStudentGradebook(student.getId(), req);

        // Lab 1 was raised from 2.0 -> 9.0: rawScore/defenseScore recalculated and accepted becomes true
        assertThat(failedLab1.getScore()).isEqualTo(9.0);
        assertThat(failedLab1.getRawScore()).isEqualTo(7.2);
        assertThat(failedLab1.getDefenseScore()).isEqualTo(1.8);
        assertThat(failedLab1.getPenaltyCoefficient()).isEqualTo(1.0);
        assertThat(failedLab1.isAccepted()).isTrue();

        // Lab 2 score did not change (10.2): existing detailed breakdown and penaltyCoefficient preserved
        assertThat(detailedLab2.getRawScore()).isEqualTo(10.0);
        assertThat(detailedLab2.getDefenseScore()).isEqualTo(2.0);
        assertThat(detailedLab2.getPenaltyCoefficient()).isEqualTo(0.85);
        assertThat(detailedLab2.isAccepted()).isTrue();
    }

    @Test
    @DisplayName("Issue #28: Token exceeding 64 characters throws IllegalArgumentException")
    void testTokenLengthValidation() {
        when(studentRepository.findById(student.getId())).thenReturn(Optional.of(student));
        String tooLongToken = "A".repeat(65);
        GradebookUpdateRequest req = new GradebookUpdateRequest(
                null, null, null, null, null, null, null, tooLongToken, null
        );

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> gradebookService.updateStudentGradebook(student.getId(), req)
        );
        verify(studentRepository, never()).save(any());
    }
}
