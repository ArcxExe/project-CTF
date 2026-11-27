package com.arcx.ctfplatform.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.arcx.ctfplatform.academic.dto.AdminProgressResponse;
import com.arcx.ctfplatform.academic.entity.AcademicGroup;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.entity.StudentStatus;
import com.arcx.ctfplatform.academic.repository.AcademicGroupRepository;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.attempts.entity.Attempt;
import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.competitions.entity.Competition;
import com.arcx.ctfplatform.competitions.entity.CompetitionStatus;
import com.arcx.ctfplatform.competitions.repository.CompetitionRepository;
import com.arcx.ctfplatform.modifiers.entity.PromoCode;
import com.arcx.ctfplatform.modifiers.entity.PromoCodeClaim;
import com.arcx.ctfplatform.modifiers.entity.PromoModifierType;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeClaimRepository;
import com.arcx.ctfplatform.modifiers.repository.ScoreAdjustmentRepository;
import com.arcx.ctfplatform.tests.entity.QuizAttempt;
import com.arcx.ctfplatform.tests.entity.QuizAttemptStatus;
import com.arcx.ctfplatform.tests.entity.TestStatus;
import com.arcx.ctfplatform.tests.entity.TestType;
import com.arcx.ctfplatform.tests.repository.QuizAttemptRepository;
import com.arcx.ctfplatform.tests.repository.TestRepository;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.users.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminProgressServiceTest {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AcademicGroupRepository academicGroupRepository;
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
    private CompetitionRepository competitionRepository;

    private AdminProgressService adminProgressService;

    @BeforeEach
    void setUp() {
        adminProgressService = new AdminProgressService(
                studentRepository,
                userRepository,
                academicGroupRepository,
                attemptRepository,
                quizAttemptRepository,
                testRepository,
                promoCodeClaimRepository,
                scoreAdjustmentRepository,
                competitionRepository
        );
    }

    @Test
    @DisplayName("Issue #28: AdminProgressService handles TEMP_MULTIPLIER, DOUBLE_COEFF value<=0 default=2, sumTestPoints, and DISQUALIFIED")
    void testUnifiedPromoCodesSumTestPointsAndDisqualified() {
        UUID groupId = UUID.randomUUID();
        AcademicGroup group = AcademicGroup.builder().id(groupId).name("БИСТ-22-1").build();

        UUID s1Id = UUID.randomUUID();
        UUID u1Id = UUID.randomUUID();
        Student s1 = Student.builder().id(s1Id).userId(u1Id).academicGroup(group).status(StudentStatus.ACTIVE).build();

        UUID s2Id = UUID.randomUUID();
        UUID u2Id = UUID.randomUUID();
        Student s2 = Student.builder().id(s2Id).userId(u2Id).academicGroup(group).status(StudentStatus.ACTIVE).build();

        UUID disqId = UUID.randomUUID();
        UUID uDisqId = UUID.randomUUID();
        Student disq = Student.builder().id(disqId).userId(uDisqId).academicGroup(group).status(StudentStatus.DISQUALIFIED).build();

        UUID compId = UUID.randomUUID();
        Competition activeComp = Competition.builder()
                .id(compId)
                .status(CompetitionStatus.ACTIVE)
                .sumTestPoints(true)
                .build();

        UUID quizId = UUID.randomUUID();
        var lectureQuiz = com.arcx.ctfplatform.tests.entity.Test.builder()
                .id(quizId)
                .competitionId(compId)
                .type(TestType.LECTURE)
                .status(TestStatus.PUBLISHED)
                .build();

        // s1: 100 CTF + 50 quiz = 150, DOUBLE_COEFF(value=0 -> default 2) => 300
        PromoCode doubleZero = PromoCode.builder()
                .id(UUID.randomUUID())
                .code("DBL0")
                .modifierType(PromoModifierType.DOUBLE_COEFF)
                .value(0)
                .build();
        // s2: 100 CTF, TEMP_MULTIPLIER(value=3) => 300
        PromoCode tempMult = PromoCode.builder()
                .id(UUID.randomUUID())
                .code("TMP3")
                .modifierType(PromoModifierType.TEMP_MULTIPLIER)
                .value(3)
                .build();

        when(studentRepository.findAll()).thenReturn(List.of(s1, s2, disq));
        when(userRepository.findAll()).thenReturn(List.of(
                User.builder().id(u1Id).username("student1").build(),
                User.builder().id(u2Id).username("student2").build(),
                User.builder().id(uDisqId).username("disq").build()
        ));
        when(academicGroupRepository.findAll()).thenReturn(List.of(group));
        when(competitionRepository.findAll()).thenReturn(List.of(activeComp));
        when(attemptRepository.findAll()).thenReturn(List.of(
                Attempt.builder().studentId(s1Id).isCorrect(true).scoreAwarded(100).build(),
                Attempt.builder().studentId(s2Id).isCorrect(true).scoreAwarded(100).build(),
                Attempt.builder().studentId(disqId).isCorrect(true).scoreAwarded(500).build()
        ));
        when(testRepository.findAll()).thenReturn(List.of(lectureQuiz));
        when(quizAttemptRepository.findAll()).thenReturn(List.of(
                QuizAttempt.builder().studentId(s1Id).quizId(quizId).status(QuizAttemptStatus.COMPLETED).score(50.0).build()
        ));
        when(promoCodeClaimRepository.findAllWithPromoCode()).thenReturn(List.of(
                PromoCodeClaim.builder().id(UUID.randomUUID()).studentId(s1Id).promoCode(doubleZero).build(),
                PromoCodeClaim.builder().id(UUID.randomUUID()).studentId(s2Id).promoCode(tempMult).build()
        ));
        when(scoreAdjustmentRepository.findAll()).thenReturn(List.of());

        List<AdminProgressResponse> result = adminProgressService.getAdminProgress();
        assertThat(result).hasSize(3);

        AdminProgressResponse r1 = result.stream().filter(r -> r.studentId().equals(s1Id)).findFirst().orElseThrow();
        AdminProgressResponse r2 = result.stream().filter(r -> r.studentId().equals(s2Id)).findFirst().orElseThrow();
        AdminProgressResponse rDisq = result.stream().filter(r -> r.studentId().equals(disqId)).findFirst().orElseThrow();

        assertThat(r1.totalScore()).isEqualTo(300);
        assertThat(r1.solvedCount()).isEqualTo(1);
        assertThat(r2.totalScore()).isEqualTo(300);
        assertThat(r2.solvedCount()).isEqualTo(1);
        assertThat(rDisq.totalScore()).isZero();
        assertThat(rDisq.solvedCount()).isZero();
    }
}
