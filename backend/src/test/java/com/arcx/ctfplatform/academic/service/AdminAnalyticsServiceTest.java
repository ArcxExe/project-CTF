package com.arcx.ctfplatform.academic.service;

import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.entity.StudentStatus;
import com.arcx.ctfplatform.academic.repository.AcademicGroupRepository;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.challenges.repository.CtfTaskRepository;
import com.arcx.ctfplatform.leaderboard.service.LeaderboardService;
import com.arcx.ctfplatform.students.repository.lab.LabScoreRepository;
import com.arcx.ctfplatform.tests.repository.QuizAttemptRepository;
import com.arcx.ctfplatform.tests.repository.TestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsServiceTest {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private AcademicGroupRepository academicGroupRepository;
    @Mock
    private LeaderboardService leaderboardService;
    @Mock
    private AttemptRepository attemptRepository;
    @Mock
    private CtfTaskRepository ctfTaskRepository;
    @Mock
    private LabScoreRepository labScoreRepository;
    @Mock
    private QuizAttemptRepository quizAttemptRepository;
    @Mock
    private TestRepository testRepository;

    private AdminAnalyticsService adminAnalyticsService;

    @BeforeEach
    void setUp() {
        adminAnalyticsService = new AdminAnalyticsService(
                studentRepository,
                academicGroupRepository,
                leaderboardService,
                attemptRepository,
                ctfTaskRepository,
                labScoreRepository,
                quizAttemptRepository,
                testRepository
        );
    }

    @Test
    @DisplayName("AdminAnalyticsService formats recommendedGrade as 'Н/Д' when recommendedGrade is null (student not eligible)")
    void testNotEligibleStudentGradeMarker() {
        UUID sId = UUID.randomUUID();
        Student student = Student.builder()
                .id(sId)
                .firstName("Иван")
                .lastName("Иванов")
                .status(StudentStatus.ACTIVE)
                .build();

        when(studentRepository.findAll()).thenReturn(List.of(student));

        LeaderboardService.LeaderboardEntry entry = new LeaderboardService.LeaderboardEntry(
                sId,
                "ivan",
                "Группа 1",
                500,
                5,
                70.0,
                70.0,
                62.5,
                68.13,
                null, // not eligible -> null grade
                false
        );

        when(leaderboardService.getLeaderboardSnapshot("GLOBAL", null)).thenReturn(List.of(entry));
        when(labScoreRepository.findByStudentId(sId)).thenReturn(Optional.empty());

        AdminAnalyticsService.AnalyticsSummary summary = adminAnalyticsService.getGroupAnalytics(null, null);

        assertThat(summary.students()).hasSize(1);
        AdminAnalyticsService.StudentAnalytics sa = summary.students().get(0);
        assertThat(sa.recommendedGrade()).isEqualTo("Н/Д");
        assertThat(sa.fullName()).isEqualTo("Иван Иванов");
    }

    @Test
    @DisplayName("AdminAnalyticsService formats recommendedGrade as string digit when grade exists")
    void testEligibleStudentGrade() {
        UUID sId = UUID.randomUUID();
        Student student = Student.builder()
                .id(sId)
                .firstName("Петр")
                .lastName("Петров")
                .status(StudentStatus.ACTIVE)
                .build();

        when(studentRepository.findAll()).thenReturn(List.of(student));

        LeaderboardService.LeaderboardEntry entry = new LeaderboardService.LeaderboardEntry(
                sId,
                "petr",
                "Группа 1",
                800,
                10,
                90.0,
                90.0,
                100.0,
                92.50,
                5,
                true
        );

        when(leaderboardService.getLeaderboardSnapshot("GLOBAL", null)).thenReturn(List.of(entry));
        when(labScoreRepository.findByStudentId(sId)).thenReturn(Optional.empty());

        AdminAnalyticsService.AnalyticsSummary summary = adminAnalyticsService.getGroupAnalytics(null, null);

        assertThat(summary.students()).hasSize(1);
        AdminAnalyticsService.StudentAnalytics sa = summary.students().get(0);
        assertThat(sa.recommendedGrade()).isEqualTo("5");
        assertThat(sa.isEligible()).isTrue();
        assertThat(sa.ctfCoeff()).isEqualTo(1.0);
        assertThat(summary.metrics().labAdmissionPercentage()).isEqualTo(100.0);
        assertThat(summary.metrics().grade5Count()).isEqualTo(1);
        assertThat(summary.metrics().grade2Count()).isZero();
    }
}
