package com.arcx.ctfplatform.leaderboard.service;

import com.arcx.ctfplatform.academic.entity.AcademicGroup;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.repository.AcademicGroupRepository;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.academic.service.GradingService;
import com.arcx.ctfplatform.attempts.entity.Attempt;
import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.competitions.repository.CompetitionRepository;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeClaimRepository;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeRepository;
import com.arcx.ctfplatform.modifiers.repository.ScoreAdjustmentRepository;
import com.arcx.ctfplatform.students.service.lab.LabScoreService;
import com.arcx.ctfplatform.tests.entity.QuizAttempt;
import com.arcx.ctfplatform.tests.entity.QuizAttemptStatus;
import com.arcx.ctfplatform.tests.entity.Test;
import com.arcx.ctfplatform.tests.entity.TestType;
import com.arcx.ctfplatform.tests.repository.QuizAttemptRepository;
import com.arcx.ctfplatform.tests.repository.TestRepository;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LabScoreService labScoreService;
    @Mock
    private GradingService gradingService;
    @Mock
    private AttemptRepository attemptRepository;
    @Mock
    private QuizAttemptRepository quizAttemptRepository;
    @Mock
    private TestRepository testRepository;
    @Mock
    private PromoCodeRepository promoCodeRepository;
    @Mock
    private PromoCodeClaimRepository promoCodeClaimRepository;
    @Mock
    private ScoreAdjustmentRepository scoreAdjustmentRepository;
    @Mock
    private CompetitionRepository competitionRepository;
    @Mock
    private AcademicGroupRepository academicGroupRepository;

    private LeaderboardService leaderboardService;

    @BeforeEach
    void setUp() {
        leaderboardService = new LeaderboardService(
                studentRepository,
                userRepository,
                labScoreService,
                gradingService,
                attemptRepository,
                quizAttemptRepository,
                testRepository,
                promoCodeRepository,
                promoCodeClaimRepository,
                scoreAdjustmentRepository,
                competitionRepository,
                academicGroupRepository
        );
        lenient().when(competitionRepository.findAll()).thenReturn(Collections.emptyList());
        lenient().when(promoCodeClaimRepository.findAllWithPromoCode()).thenReturn(Collections.emptyList());
        lenient().when(scoreAdjustmentRepository.findAll()).thenReturn(Collections.emptyList());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Acceptance Criterion 1: B_lab = 110, B_kt = 72, B_ctf = 560 -> L = 76.92%, K = 72.00%, C = 70.00% -> S = 73.96% -> Grade 4")
    void testCriterion1CourseFormula() {
        UUID studentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).userId(userId).studentCode("S1").build();
        User user = User.builder().id(userId).username("student1").build();

        when(studentRepository.findAll()).thenReturn(List.of(student));
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(academicGroupRepository.findAll()).thenReturn(Collections.emptyList());

        // CTF score: 560
        UUID taskId = UUID.randomUUID();
        Attempt attempt = Attempt.builder()
                .studentId(studentId)
                .taskId(taskId)
                .isCorrect(true)
                .scoreAwarded(560)
                .submittedAt(Instant.now())
                .build();
        when(attemptRepository.findAll()).thenReturn(List.of(attempt));

        // Control points: 3 KT quizzes totalling 72 points (e.g. 25 + 25 + 22)
        UUID kt1Id = UUID.randomUUID();
        UUID kt2Id = UUID.randomUUID();
        UUID kt3Id = UUID.randomUUID();
        Test kt1 = Test.builder().id(kt1Id).title("КТ-1").type(TestType.CONTROL_POINT).build();
        Test kt2 = Test.builder().id(kt2Id).title("КТ-2").type(TestType.CONTROL_POINT).build();
        Test kt3 = Test.builder().id(kt3Id).title("КТ-3").type(TestType.CONTROL_POINT).build();
        when(testRepository.findAll()).thenReturn(List.of(kt1, kt2, kt3));

        QuizAttempt qa1 = QuizAttempt.builder().studentId(studentId).quizId(kt1Id).status(QuizAttemptStatus.COMPLETED).score(25).build();
        QuizAttempt qa2 = QuizAttempt.builder().studentId(studentId).quizId(kt2Id).status(QuizAttemptStatus.COMPLETED).score(25).build();
        QuizAttempt qa3 = QuizAttempt.builder().studentId(studentId).quizId(kt3Id).status(QuizAttemptStatus.COMPLETED).score(22).build();
        when(quizAttemptRepository.findAll()).thenReturn(List.of(qa1, qa2, qa3));

        // Lab score: 110, eligible = true
        when(labScoreService.getFinalLabScore(studentId)).thenReturn(110.0);
        when(labScoreService.isEligible(studentId)).thenReturn(true);
        when(gradingService.calculateGrade(73.96, 76.92, 72.00)).thenReturn(4);

        List<LeaderboardService.LeaderboardEntry> leaderboard = leaderboardService.getLeaderboardSnapshot(null, null);

        assertThat(leaderboard).hasSize(1);
        LeaderboardService.LeaderboardEntry entry = leaderboard.get(0);
        assertThat(entry.ctfScore()).isEqualTo(560);
        assertThat(entry.lPercent()).isEqualTo(76.92);
        assertThat(entry.kPercent()).isEqualTo(72.00);
        assertThat(entry.cPercent()).isEqualTo(70.00);
        assertThat(entry.sScore()).isEqualTo(73.96);
        assertThat(entry.recommendedGrade()).isEqualTo(4);
        assertThat(entry.isEligible()).isTrue();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Acceptance Criterion 2: B_ctf = 950 -> ctfScore = 950, cPercent = 100.00% (capped at 100)")
    void testCriterion2CtfCap() {
        UUID studentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).userId(userId).studentCode("S1").build();
        User user = User.builder().id(userId).username("student1").build();

        when(studentRepository.findAll()).thenReturn(List.of(student));
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(academicGroupRepository.findAll()).thenReturn(Collections.emptyList());

        Attempt attempt = Attempt.builder()
                .studentId(studentId)
                .taskId(UUID.randomUUID())
                .isCorrect(true)
                .scoreAwarded(950)
                .submittedAt(Instant.now())
                .build();
        when(attemptRepository.findAll()).thenReturn(List.of(attempt));

        when(testRepository.findAll()).thenReturn(Collections.emptyList());
        when(quizAttemptRepository.findAll()).thenReturn(Collections.emptyList());
        when(labScoreService.getFinalLabScore(studentId)).thenReturn(0.0);
        when(labScoreService.isEligible(studentId)).thenReturn(true);
        when(gradingService.calculateGrade(anyDouble(), anyDouble(), anyDouble())).thenReturn(2);

        List<LeaderboardService.LeaderboardEntry> leaderboard = leaderboardService.getLeaderboardSnapshot(null, null);

        assertThat(leaderboard).hasSize(1);
        LeaderboardService.LeaderboardEntry entry = leaderboard.get(0);
        assertThat(entry.ctfScore()).isEqualTo(950);
        assertThat(entry.cPercent()).isEqualTo(100.00);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Acceptance Criterion 4: Student with 7 out of 8 labs closed has isEligible = false and recommendedGrade = null")
    void testCriterion4IneligibleStudentGradeNull() {
        UUID studentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).userId(userId).studentCode("S1").build();
        User user = User.builder().id(userId).username("student1").build();

        when(studentRepository.findAll()).thenReturn(List.of(student));
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(academicGroupRepository.findAll()).thenReturn(Collections.emptyList());

        // High CTF and KT scores
        Attempt attempt = Attempt.builder()
                .studentId(studentId)
                .taskId(UUID.randomUUID())
                .isCorrect(true)
                .scoreAwarded(800)
                .submittedAt(Instant.now())
                .build();
        when(attemptRepository.findAll()).thenReturn(List.of(attempt));

        when(testRepository.findAll()).thenReturn(Collections.emptyList());
        when(quizAttemptRepository.findAll()).thenReturn(Collections.emptyList());

        // 7 of 8 labs passed
        when(labScoreService.getFinalLabScore(studentId)).thenReturn(100.0);
        when(labScoreService.isEligible(studentId)).thenReturn(false);

        List<LeaderboardService.LeaderboardEntry> leaderboard = leaderboardService.getLeaderboardSnapshot(null, null);

        assertThat(leaderboard).hasSize(1);
        LeaderboardService.LeaderboardEntry entry = leaderboard.get(0);
        assertThat(entry.isEligible()).isFalse();
        assertThat(entry.recommendedGrade()).isNull();
        verify(gradingService, never()).calculateGrade(anyDouble(), anyDouble(), anyDouble());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Acceptance Criterion 5: Independence from group - adding new leader does not alter other students' scores")
    void testCriterion5IndependenceFromGroup() {
        UUID s1Id = UUID.randomUUID();
        UUID s1UserId = UUID.randomUUID();
        Student s1 = Student.builder().id(s1Id).userId(s1UserId).studentCode("S1").build();
        User u1 = User.builder().id(s1UserId).username("s1").build();

        UUID s2Id = UUID.randomUUID();
        UUID s2UserId = UUID.randomUUID();
        Student s2 = Student.builder().id(s2Id).userId(s2UserId).studentCode("S2").build();
        User u2 = User.builder().id(s2UserId).username("s2").build();

        when(studentRepository.findAll()).thenReturn(List.of(s1, s2));
        when(userRepository.findAll()).thenReturn(List.of(u1, u2));
        when(academicGroupRepository.findAll()).thenReturn(Collections.emptyList());

        // s1 has 400 points, s2 has 10,000 points (huge score difference)
        Attempt a1 = Attempt.builder().studentId(s1Id).taskId(UUID.randomUUID()).isCorrect(true).scoreAwarded(400).build();
        Attempt a2 = Attempt.builder().studentId(s2Id).taskId(UUID.randomUUID()).isCorrect(true).scoreAwarded(10000).build();
        when(attemptRepository.findAll()).thenReturn(List.of(a1, a2));

        when(testRepository.findAll()).thenReturn(Collections.emptyList());
        when(quizAttemptRepository.findAll()).thenReturn(Collections.emptyList());

        when(labScoreService.getFinalLabScore(s1Id)).thenReturn(110.0);
        when(labScoreService.isEligible(s1Id)).thenReturn(true);

        when(labScoreService.getFinalLabScore(s2Id)).thenReturn(143.0);
        when(labScoreService.isEligible(s2Id)).thenReturn(true);

        when(gradingService.calculateGrade(anyDouble(), anyDouble(), anyDouble())).thenReturn(3);

        List<LeaderboardService.LeaderboardEntry> leaderboard = leaderboardService.getLeaderboardSnapshot(null, null);

        assertThat(leaderboard).hasSize(2);
        LeaderboardService.LeaderboardEntry s1Entry = leaderboard.stream().filter(e -> e.studentId().equals(s1Id)).findFirst().orElseThrow();

        // 400 / 800 * 100 = 50.00% regardless of s2's 10,000 points!
        assertThat(s1Entry.cPercent()).isEqualTo(50.00);
        assertThat(s1Entry.lPercent()).isEqualTo(76.92);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Tests separation: lecture quizzes are excluded from K and CTF scores, control points capped at 25 per KT")
    void testLectureQuizzesExcludedFromKtAndCtf() {
        UUID studentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).userId(userId).studentCode("S1").build();
        User user = User.builder().id(userId).username("student1").build();

        when(studentRepository.findAll()).thenReturn(List.of(student));
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(academicGroupRepository.findAll()).thenReturn(Collections.emptyList());

        when(attemptRepository.findAll()).thenReturn(Collections.emptyList());

        // 1 Control point (score 30, capped at 25)
        UUID ktId = UUID.randomUUID();
        Test kt = Test.builder().id(ktId).title("КТ-1").type(TestType.CONTROL_POINT).build();

        // 1 Lecture quiz (score 50, should be completely ignored)
        UUID lecId = UUID.randomUUID();
        Test lec = Test.builder().id(lecId).title("Входной тест к лекции 1").type(TestType.LECTURE).build();

        when(testRepository.findAll()).thenReturn(List.of(kt, lec));

        QuizAttempt qaKt = QuizAttempt.builder().studentId(studentId).quizId(ktId).status(QuizAttemptStatus.COMPLETED).score(30).build();
        QuizAttempt qaLec = QuizAttempt.builder().studentId(studentId).quizId(lecId).status(QuizAttemptStatus.COMPLETED).score(50).build();
        when(quizAttemptRepository.findAll()).thenReturn(List.of(qaKt, qaLec));

        when(labScoreService.getFinalLabScore(studentId)).thenReturn(0.0);
        when(labScoreService.isEligible(studentId)).thenReturn(true);
        when(gradingService.calculateGrade(anyDouble(), anyDouble(), anyDouble())).thenReturn(2);

        List<LeaderboardService.LeaderboardEntry> leaderboard = leaderboardService.getLeaderboardSnapshot(null, null);

        assertThat(leaderboard).hasSize(1);
        LeaderboardService.LeaderboardEntry entry = leaderboard.get(0);
        // CTF score should be 0 (no quiz points added!)
        assertThat(entry.ctfScore()).isEqualTo(0);
        // K percent: only KT counts, capped at 25 points -> 25 / 100 * 100 = 25.00%
        assertThat(entry.kPercent()).isEqualTo(25.00);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Sorting: ordered by CTF score descending, tie-broken by earliest transaction time")
    void testSortingAndTieBreaking() {
        UUID s1Id = UUID.randomUUID();
        UUID s1UserId = UUID.randomUUID();
        Student s1 = Student.builder().id(s1Id).userId(s1UserId).studentCode("S1").build();
        User u1 = User.builder().id(s1UserId).username("alice").build();

        UUID s2Id = UUID.randomUUID();
        UUID s2UserId = UUID.randomUUID();
        Student s2 = Student.builder().id(s2Id).userId(s2UserId).studentCode("S2").build();
        User u2 = User.builder().id(s2UserId).username("bob").build();

        when(studentRepository.findAll()).thenReturn(List.of(s1, s2));
        when(userRepository.findAll()).thenReturn(List.of(u1, u2));
        when(academicGroupRepository.findAll()).thenReturn(Collections.emptyList());

        // Both have 500 CTF points, but Alice solved at 10:00, Bob solved at 11:00
        Instant tAlice = Instant.parse("2026-10-14T10:00:00Z");
        Instant tBob = Instant.parse("2026-10-14T11:00:00Z");

        Attempt a1 = Attempt.builder().studentId(s1Id).taskId(UUID.randomUUID()).isCorrect(true).scoreAwarded(500).submittedAt(tAlice).build();
        Attempt a2 = Attempt.builder().studentId(s2Id).taskId(UUID.randomUUID()).isCorrect(true).scoreAwarded(500).submittedAt(tBob).build();
        when(attemptRepository.findAll()).thenReturn(List.of(a1, a2));

        when(testRepository.findAll()).thenReturn(Collections.emptyList());
        when(quizAttemptRepository.findAll()).thenReturn(Collections.emptyList());
        when(labScoreService.getFinalLabScore(any())).thenReturn(0.0);
        when(labScoreService.isEligible(any())).thenReturn(true);
        when(gradingService.calculateGrade(anyDouble(), anyDouble(), anyDouble())).thenReturn(2);

        List<LeaderboardService.LeaderboardEntry> leaderboard = leaderboardService.getLeaderboardSnapshot(null, null);

        assertThat(leaderboard).hasSize(2);
        // Alice should be first because she solved earlier
        assertThat(leaderboard.get(0).studentId()).isEqualTo(s1Id);
        assertThat(leaderboard.get(1).studentId()).isEqualTo(s2Id);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Regulation 11.7: Equal CTF score and equal timestamp tie-broken by fewer wrong attempts")
    void testSortingTieBrokenByWrongAttempts() {
        UUID s1Id = UUID.randomUUID();
        UUID s1UserId = UUID.randomUUID();
        Student s1 = Student.builder().id(s1Id).userId(s1UserId).studentCode("S1").build();
        User u1 = User.builder().id(s1UserId).username("alice").build();

        UUID s2Id = UUID.randomUUID();
        UUID s2UserId = UUID.randomUUID();
        Student s2 = Student.builder().id(s2Id).userId(s2UserId).studentCode("S2").build();
        User u2 = User.builder().id(s2UserId).username("bob").build();

        when(studentRepository.findAll()).thenReturn(List.of(s1, s2));
        when(userRepository.findAll()).thenReturn(List.of(u1, u2));
        when(academicGroupRepository.findAll()).thenReturn(Collections.emptyList());

        Instant sameTime = Instant.parse("2026-10-14T10:00:00Z");

        // Alice: 1 correct (500 pts), 1 incorrect attempt
        Attempt a1Correct = Attempt.builder().studentId(s1Id).taskId(UUID.randomUUID()).isCorrect(true).scoreAwarded(500).submittedAt(sameTime).build();
        Attempt a1Wrong = Attempt.builder().studentId(s1Id).taskId(UUID.randomUUID()).isCorrect(false).submittedAt(sameTime.minusSeconds(100)).build();

        // Bob: 1 correct (500 pts), 3 incorrect attempts
        Attempt a2Correct = Attempt.builder().studentId(s2Id).taskId(UUID.randomUUID()).isCorrect(true).scoreAwarded(500).submittedAt(sameTime).build();
        Attempt a2Wrong1 = Attempt.builder().studentId(s2Id).taskId(UUID.randomUUID()).isCorrect(false).submittedAt(sameTime.minusSeconds(200)).build();
        Attempt a2Wrong2 = Attempt.builder().studentId(s2Id).taskId(UUID.randomUUID()).isCorrect(false).submittedAt(sameTime.minusSeconds(150)).build();
        Attempt a2Wrong3 = Attempt.builder().studentId(s2Id).taskId(UUID.randomUUID()).isCorrect(false).submittedAt(sameTime.minusSeconds(100)).build();

        when(attemptRepository.findAll()).thenReturn(List.of(a1Correct, a1Wrong, a2Correct, a2Wrong1, a2Wrong2, a2Wrong3));

        when(testRepository.findAll()).thenReturn(Collections.emptyList());
        when(quizAttemptRepository.findAll()).thenReturn(Collections.emptyList());
        when(labScoreService.getFinalLabScore(any())).thenReturn(0.0);
        when(labScoreService.isEligible(any())).thenReturn(true);
        when(gradingService.calculateGrade(anyDouble(), anyDouble(), anyDouble())).thenReturn(2);

        List<LeaderboardService.LeaderboardEntry> leaderboard = leaderboardService.getLeaderboardSnapshot(null, null);

        assertThat(leaderboard).hasSize(2);
        // Alice should be first because she has 1 wrong attempt vs Bob's 3
        assertThat(leaderboard.get(0).studentId()).isEqualTo(s1Id);
        assertThat(leaderboard.get(1).studentId()).isEqualTo(s2Id);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Regulation 5.4: Fractional KT scores (e.g. 17.5 for reserve attempt) are supported and properly summed")
    void testFractionalKtScore() {
        UUID studentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).userId(userId).studentCode("S1").build();
        User user = User.builder().id(userId).username("student1").build();

        when(studentRepository.findAll()).thenReturn(List.of(student));
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(academicGroupRepository.findAll()).thenReturn(Collections.emptyList());

        when(attemptRepository.findAll()).thenReturn(Collections.emptyList());

        UUID kt1Id = UUID.randomUUID();
        UUID kt2Id = UUID.randomUUID();
        Test kt1 = Test.builder().id(kt1Id).title("КТ-1").type(TestType.CONTROL_POINT).build();
        Test kt2 = Test.builder().id(kt2Id).title("КТ-2").type(TestType.CONTROL_POINT).build();
        when(testRepository.findAll()).thenReturn(List.of(kt1, kt2));

        // KT1 has full 25.0, KT2 has reserve attempt 17.5
        QuizAttempt qa1 = QuizAttempt.builder().studentId(studentId).quizId(kt1Id).status(QuizAttemptStatus.COMPLETED).score(25.0).build();
        QuizAttempt qa2 = QuizAttempt.builder().studentId(studentId).quizId(kt2Id).status(QuizAttemptStatus.COMPLETED).score(17.5).build();
        when(quizAttemptRepository.findAll()).thenReturn(List.of(qa1, qa2));

        when(labScoreService.getFinalLabScore(studentId)).thenReturn(0.0);
        when(labScoreService.isEligible(studentId)).thenReturn(true);
        when(gradingService.calculateGrade(anyDouble(), anyDouble(), anyDouble())).thenReturn(2);

        List<LeaderboardService.LeaderboardEntry> leaderboard = leaderboardService.getLeaderboardSnapshot(null, null);

        assertThat(leaderboard).hasSize(1);
        LeaderboardService.LeaderboardEntry entry = leaderboard.get(0);
        // 25.0 + 17.5 = 42.50%
        assertThat(entry.kPercent()).isEqualTo(42.50);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Requirement 3.1: LeaderboardEntry record has only the 11 specified components and no obsolete getters")
    void testRecordFieldsStrictCompliance() {
        var components = LeaderboardService.LeaderboardEntry.class.getRecordComponents();
        var componentNames = java.util.Arrays.stream(components).map(java.lang.reflect.RecordComponent::getName).toList();
        assertThat(componentNames).containsExactly(
                "studentId",
                "username",
                "groupName",
                "ctfScore",
                "solvedCount",
                "lPercent",
                "kPercent",
                "cPercent",
                "sScore",
                "recommendedGrade",
                "isEligible"
        );

        // Verify obsolete methods do NOT exist
        var methodNames = java.util.Arrays.stream(LeaderboardService.LeaderboardEntry.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .toList();
        assertThat(methodNames).doesNotContain("score", "v1", "v2", "sCoefficient");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("CTF score with large negative adjustment is clamped to zero")
    void testNegativeAdjustmentClampedAtZero() {
        UUID studentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).userId(userId).studentCode("S1").build();
        User user = User.builder().id(userId).username("student1").build();

        when(studentRepository.findAll()).thenReturn(List.of(student));
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(academicGroupRepository.findAll()).thenReturn(Collections.emptyList());

        // 100 raw points
        Attempt attempt = Attempt.builder()
                .studentId(studentId)
                .taskId(UUID.randomUUID())
                .isCorrect(true)
                .scoreAwarded(100)
                .build();
        when(attemptRepository.findAll()).thenReturn(List.of(attempt));

        // Penalty adjustment of -500 points
        com.arcx.ctfplatform.modifiers.entity.ScoreAdjustment adj = com.arcx.ctfplatform.modifiers.entity.ScoreAdjustment.builder()
                .studentId(studentId)
                .points(-500)
                .build();
        when(scoreAdjustmentRepository.findAll()).thenReturn(List.of(adj));

        when(testRepository.findAll()).thenReturn(Collections.emptyList());
        when(quizAttemptRepository.findAll()).thenReturn(Collections.emptyList());
        when(labScoreService.getFinalLabScore(studentId)).thenReturn(0.0);
        when(labScoreService.isEligible(studentId)).thenReturn(true);
        when(gradingService.calculateGrade(anyDouble(), anyDouble(), anyDouble())).thenReturn(2);

        List<LeaderboardService.LeaderboardEntry> leaderboard = leaderboardService.getLeaderboardSnapshot(null, null);

        assertThat(leaderboard).hasSize(1);
        LeaderboardService.LeaderboardEntry entry = leaderboard.get(0);
        assertThat(entry.ctfScore()).isEqualTo(0);
        assertThat(entry.cPercent()).isEqualTo(0.00);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("End-to-end integration: real GradingService calculation without mocks for Criteria 1 and 3")
    void testRealGradingServiceIntegration() {
        GradingService realGradingService = new GradingService(null);
        LeaderboardService serviceWithRealGrading = new LeaderboardService(
                studentRepository,
                userRepository,
                labScoreService,
                realGradingService,
                attemptRepository,
                quizAttemptRepository,
                testRepository,
                promoCodeRepository,
                promoCodeClaimRepository,
                scoreAdjustmentRepository,
                competitionRepository,
                academicGroupRepository
        );

        UUID s1Id = UUID.randomUUID();
        UUID s2Id = UUID.randomUUID();
        UUID u1Id = UUID.randomUUID();
        UUID u2Id = UUID.randomUUID();

        Student s1 = Student.builder().id(s1Id).userId(u1Id).studentCode("S1").build();
        Student s2 = Student.builder().id(s2Id).userId(u2Id).studentCode("S2").build();
        User u1 = User.builder().id(u1Id).username("s1").build();
        User u2 = User.builder().id(u2Id).username("s2").build();

        when(studentRepository.findAll()).thenReturn(List.of(s1, s2));
        when(userRepository.findAll()).thenReturn(List.of(u1, u2));
        when(academicGroupRepository.findAll()).thenReturn(Collections.emptyList());

        Attempt a1 = Attempt.builder().studentId(s1Id).taskId(UUID.randomUUID()).isCorrect(true).scoreAwarded(560).build();
        Attempt a2 = Attempt.builder().studentId(s2Id).taskId(UUID.randomUUID()).isCorrect(true).scoreAwarded(800).build();
        when(attemptRepository.findAll()).thenReturn(List.of(a1, a2));

        // Control points
        UUID kt1 = UUID.randomUUID();
        UUID kt2 = UUID.randomUUID();
        Test t1 = Test.builder().id(kt1).title("КТ-1").type(TestType.CONTROL_POINT).build();
        Test t2 = Test.builder().id(kt2).title("КТ-2").type(TestType.CONTROL_POINT).build();

        UUID kt3 = UUID.randomUUID();
        Test t3 = Test.builder().id(kt3).title("КТ-3").type(TestType.CONTROL_POINT).build();

        QuizAttempt qa11 = QuizAttempt.builder().studentId(s1Id).quizId(kt1).status(QuizAttemptStatus.COMPLETED).score(25).build();
        QuizAttempt qa12 = QuizAttempt.builder().studentId(s1Id).quizId(kt2).status(QuizAttemptStatus.COMPLETED).score(25).build();
        QuizAttempt qa13 = QuizAttempt.builder().studentId(s1Id).quizId(kt3).status(QuizAttemptStatus.COMPLETED).score(22).build();

        UUID kt4 = UUID.randomUUID();
        Test t4 = Test.builder().id(kt4).title("КТ-4").type(TestType.CONTROL_POINT).build();
        when(testRepository.findAll()).thenReturn(List.of(t1, t2, t3, t4));

        QuizAttempt qa21 = QuizAttempt.builder().studentId(s2Id).quizId(kt1).status(QuizAttemptStatus.COMPLETED).score(25).build();
        QuizAttempt qa22 = QuizAttempt.builder().studentId(s2Id).quizId(kt2).status(QuizAttemptStatus.COMPLETED).score(25).build();
        QuizAttempt qa23 = QuizAttempt.builder().studentId(s2Id).quizId(kt3).status(QuizAttemptStatus.COMPLETED).score(25).build();
        QuizAttempt qa24 = QuizAttempt.builder().studentId(s2Id).quizId(kt4).status(QuizAttemptStatus.COMPLETED).score(5).build();

        when(quizAttemptRepository.findAll()).thenReturn(List.of(qa11, qa12, qa13, qa21, qa22, qa23, qa24));

        when(labScoreService.getFinalLabScore(s1Id)).thenReturn(110.0);
        when(labScoreService.isEligible(s1Id)).thenReturn(true);

        when(labScoreService.getFinalLabScore(s2Id)).thenReturn(114.4); // 114.4 / 143 * 100 = 80.00%
        when(labScoreService.isEligible(s2Id)).thenReturn(true);

        List<LeaderboardService.LeaderboardEntry> leaderboard = serviceWithRealGrading.getLeaderboardSnapshot(null, null);

        LeaderboardService.LeaderboardEntry e1 = leaderboard.stream().filter(e -> e.studentId().equals(s1Id)).findFirst().orElseThrow();
        assertThat(e1.sScore()).isEqualTo(73.96);
        assertThat(e1.recommendedGrade()).isEqualTo(4);

        LeaderboardService.LeaderboardEntry e2 = leaderboard.stream().filter(e -> e.studentId().equals(s2Id)).findFirst().orElseThrow();
        assertThat(e2.lPercent()).isEqualTo(80.00);
        assertThat(e2.kPercent()).isEqualTo(80.00);
        assertThat(e2.cPercent()).isEqualTo(100.00);
        assertThat(e2.sScore()).isEqualTo(85.00);
        assertThat(e2.recommendedGrade()).isEqualTo(5);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("OUT_OF_RATING students are excluded for non-admins and DISQUALIFIED students have zeroed scores and grade 2")
    void testOutOfRatingAndDisqualifiedSemantics() {
        UUID outId = UUID.randomUUID();
        UUID disqId = UUID.randomUUID();
        UUID uOutId = UUID.randomUUID();
        UUID uDisqId = UUID.randomUUID();

        Student outStudent = Student.builder()
                .id(outId)
                .userId(uOutId)
                .studentCode("S-OUT")
                .status(com.arcx.ctfplatform.academic.entity.StudentStatus.OUT_OF_RATING)
                .build();
        Student disqStudent = Student.builder()
                .id(disqId)
                .userId(uDisqId)
                .studentCode("S-DISQ")
                .status(com.arcx.ctfplatform.academic.entity.StudentStatus.DISQUALIFIED)
                .build();

        when(studentRepository.findAll()).thenReturn(List.of(outStudent, disqStudent));
        when(userRepository.findAll()).thenReturn(List.of(
                User.builder().id(uOutId).username("out_user").build(),
                User.builder().id(uDisqId).username("disq_user").build()
        ));
        when(academicGroupRepository.findAll()).thenReturn(Collections.emptyList());
        when(attemptRepository.findAll()).thenReturn(List.of(
                Attempt.builder().studentId(disqId).taskId(UUID.randomUUID()).isCorrect(true).scoreAwarded(500).build()
        ));
        when(testRepository.findAll()).thenReturn(Collections.emptyList());
        when(quizAttemptRepository.findAll()).thenReturn(Collections.emptyList());

        List<LeaderboardService.LeaderboardEntry> leaderboard = leaderboardService.getLeaderboardSnapshot(null, null);

        assertThat(leaderboard).hasSize(1);
        LeaderboardService.LeaderboardEntry disqEntry = leaderboard.get(0);
        assertThat(disqEntry.studentId()).isEqualTo(disqId);
        assertThat(disqEntry.ctfScore()).isZero();
        assertThat(disqEntry.solvedCount()).isZero();
        assertThat(disqEntry.sScore()).isZero();
        assertThat(disqEntry.recommendedGrade()).isEqualTo(2);
        assertThat(disqEntry.isEligible()).isFalse();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("DOUBLE_COEFF with value <= 0 defaults to 2 and activeComp.isSumTestPoints() adds test points")
    void testDoubleCoeffDefaultAndSumTestPoints() {
        UUID studentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).userId(userId).studentCode("S1").build();
        User user = User.builder().id(userId).username("student1").build();

        UUID compId = UUID.randomUUID();
        com.arcx.ctfplatform.competitions.entity.Competition activeComp =
                com.arcx.ctfplatform.competitions.entity.Competition.builder()
                        .id(compId)
                        .status(com.arcx.ctfplatform.competitions.entity.CompetitionStatus.ACTIVE)
                        .sumTestPoints(true)
                        .build();
        when(competitionRepository.findAll()).thenReturn(List.of(activeComp));

        when(studentRepository.findAll()).thenReturn(List.of(student));
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(academicGroupRepository.findAll()).thenReturn(Collections.emptyList());

        Attempt attempt = Attempt.builder().studentId(studentId).taskId(UUID.randomUUID()).isCorrect(true).scoreAwarded(100).build();
        when(attemptRepository.findAll()).thenReturn(List.of(attempt));

        UUID testId = UUID.randomUUID();
        Test test = Test.builder().id(testId).competitionId(compId).type(TestType.LECTURE).build();
        when(testRepository.findAll()).thenReturn(List.of(test));
        QuizAttempt qa = QuizAttempt.builder().studentId(studentId).quizId(testId).status(QuizAttemptStatus.COMPLETED).score(50).build();
        when(quizAttemptRepository.findAll()).thenReturn(List.of(qa));

        com.arcx.ctfplatform.modifiers.entity.PromoCode promo = com.arcx.ctfplatform.modifiers.entity.PromoCode.builder()
                .id(UUID.randomUUID())
                .code("DOUBLE")
                .modifierType(com.arcx.ctfplatform.modifiers.entity.PromoModifierType.DOUBLE_COEFF)
                .value(0)
                .build();
        com.arcx.ctfplatform.modifiers.entity.PromoCodeClaim claim = com.arcx.ctfplatform.modifiers.entity.PromoCodeClaim.builder()
                .id(UUID.randomUUID())
                .studentId(studentId)
                .promoCode(promo)
                .build();
        when(promoCodeClaimRepository.findAllWithPromoCode()).thenReturn(List.of(claim));

        when(labScoreService.getFinalLabScore(studentId)).thenReturn(0.0);
        when(labScoreService.isEligible(studentId)).thenReturn(true);
        when(gradingService.calculateGrade(anyDouble(), anyDouble(), anyDouble())).thenReturn(2);

        List<LeaderboardService.LeaderboardEntry> leaderboard = leaderboardService.getLeaderboardSnapshot(null, null);

        assertThat(leaderboard).hasSize(1);
        // (100 task + 50 quiz) * 2 (default DOUBLE_COEFF) = 300
        assertThat(leaderboard.get(0).ctfScore()).isEqualTo(300);
    }
}
