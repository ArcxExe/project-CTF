package com.arcx.ctfplatform.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.arcx.ctfplatform.academic.attendance.repository.StudentAttendanceRepository;
import com.arcx.ctfplatform.academic.dto.ResetMode;
import com.arcx.ctfplatform.academic.dto.ResetRequestDto;
import com.arcx.ctfplatform.academic.dto.ResetResultDto;
import com.arcx.ctfplatform.academic.entity.AcademicFlow;
import com.arcx.ctfplatform.academic.entity.AcademicGroup;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.repository.AcademicFlowRepository;
import com.arcx.ctfplatform.academic.repository.AcademicGroupRepository;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.audit.service.AuditService;
import com.arcx.ctfplatform.challenges.repository.CtfTaskRepository;
import com.arcx.ctfplatform.leaderboard.service.LeaderboardService;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeClaimRepository;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeRepository;
import com.arcx.ctfplatform.modifiers.repository.ScoreAdjustmentRepository;
import com.arcx.ctfplatform.students.repository.lab.LabScoreHistoryRepository;
import com.arcx.ctfplatform.students.repository.lab.LabScoreRepository;
import com.arcx.ctfplatform.students.repository.lab.StudentLabRepository;
import com.arcx.ctfplatform.tests.repository.QuizAttemptRepository;
import com.arcx.ctfplatform.users.entity.Role;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AcademicResetServiceTest {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AcademicGroupRepository groupRepository;
    @Mock
    private AcademicFlowRepository flowRepository;
    @Mock
    private AttemptRepository attemptRepository;
    @Mock
    private QuizAttemptRepository quizAttemptRepository;
    @Mock
    private PromoCodeClaimRepository promoCodeClaimRepository;
    @Mock
    private PromoCodeRepository promoCodeRepository;
    @Mock
    private ScoreAdjustmentRepository scoreAdjustmentRepository;
    @Mock
    private StudentLabRepository studentLabRepository;
    @Mock
    private LabScoreRepository labScoreRepository;
    @Mock
    private LabScoreHistoryRepository labScoreHistoryRepository;
    @Mock
    private StudentAttendanceRepository attendanceRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private LeaderboardService leaderboardService;
    @Mock
    private EntityManager entityManager;
    @Mock
    private CtfTaskRepository ctfTaskRepository;

    private AcademicResetService resetService;

    private User adminUser;

    @BeforeEach
    void setUp() {
        resetService = new AcademicResetService(
                studentRepository,
                userRepository,
                groupRepository,
                flowRepository,
                attemptRepository,
                quizAttemptRepository,
                promoCodeClaimRepository,
                promoCodeRepository,
                scoreAdjustmentRepository,
                studentLabRepository,
                labScoreRepository,
                labScoreHistoryRepository,
                attendanceRepository,
                auditService,
                leaderboardService,
                entityManager
        );

        adminUser = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .role(Role.ADMIN)
                .build();
    }

    @Test
    @DisplayName("1. При неправильной фразе подтверждения выбрасывается исключение и ничего не удаляется")
    void testInvalidConfirmationPhraseThrowsExceptionAndDeletesNothing() {
        ResetRequestDto request = ResetRequestDto.builder()
                .mode(ResetMode.FULL_SEMESTER_WIPE)
                .confirmationPhrase("WRONG_PHRASE")
                .build();

        assertThatThrownBy(() -> resetService.resetSemester(request, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Неверная фраза подтверждения");

        // Verify null phrase rejected
        assertThatThrownBy(() -> resetService.resetSemester(
                ResetRequestDto.builder().mode(ResetMode.FULL_SEMESTER_WIPE).confirmationPhrase(null).build(), adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Фраза подтверждения обязательна");

        // Verify blank phrase rejected
        assertThatThrownBy(() -> resetService.resetSemester(
                ResetRequestDto.builder().mode(ResetMode.FULL_SEMESTER_WIPE).confirmationPhrase("   ").build(), adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Фраза подтверждения обязательна");

        // Verify missing mode rejected
        assertThatThrownBy(() -> resetService.resetSemester(
                ResetRequestDto.builder().mode(null).confirmationPhrase("RESET").build(), adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Режим очистки обязателен");

        // Verify that NO repositories were invoked to delete anything
        verifyNoInteractions(attemptRepository);
        verifyNoInteractions(quizAttemptRepository);
        verifyNoInteractions(promoCodeClaimRepository);
        verifyNoInteractions(scoreAdjustmentRepository);
        verifyNoInteractions(studentLabRepository);
        verifyNoInteractions(labScoreRepository);
        verifyNoInteractions(labScoreHistoryRepository);
        verifyNoInteractions(attendanceRepository);
        verifyNoInteractions(studentRepository);
        verifyNoInteractions(userRepository);
        verifyNoInteractions(groupRepository);
        verifyNoInteractions(flowRepository);
        verifyNoInteractions(auditService);
        verifyNoInteractions(leaderboardService);
        verifyNoInteractions(ctfTaskRepository);
    }

    @Test
    @DisplayName("2. В режиме FULL_SEMESTER_WIPE удаляются студенты и их результаты, но ADMIN и INSTRUCTOR пользователи и задачи CTF остаются")
    void testFullSemesterWipeDeletesStudentsAndResultsPreservingAdminAndTasks() {
        UUID studentUserId = UUID.randomUUID();
        UUID adminUserId = adminUser.getId();
        UUID instructorUserId = UUID.randomUUID();

        User studentUser = User.builder()
                .id(studentUserId)
                .username("student1")
                .role(Role.STUDENT)
                .build();

        User instructorUser = User.builder()
                .id(instructorUserId)
                .username("teacher1")
                .role(Role.INSTRUCTOR)
                .build();

        Student student1 = Student.builder()
                .id(UUID.randomUUID())
                .userId(studentUserId)
                .studentCode("STU-100")
                .build();

        Student student2 = Student.builder()
                .id(UUID.randomUUID())
                .userId(adminUserId)
                .studentCode("STU-101")
                .build();

        Student student3 = Student.builder()
                .id(UUID.randomUUID())
                .userId(instructorUserId)
                .studentCode("STU-102")
                .build();

        when(studentRepository.findAll()).thenReturn(List.of(student1, student2, student3));
        when(attemptRepository.deleteAllAttempts()).thenReturn(15);
        when(quizAttemptRepository.deleteAllQuizAttempts()).thenReturn(8);
        when(labScoreHistoryRepository.deleteAllHistory()).thenReturn(4);
        when(studentLabRepository.deleteAllStudentLabs()).thenReturn(6);
        when(labScoreRepository.deleteAllScores()).thenReturn(2);
        when(studentRepository.deleteAllStudents()).thenReturn(3);
        when(groupRepository.deleteAllGroups()).thenReturn(3);

        when(userRepository.findById(studentUserId)).thenReturn(Optional.of(studentUser));
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(instructorUserId)).thenReturn(Optional.of(instructorUser));

        ResetRequestDto request = ResetRequestDto.builder()
                .mode(ResetMode.FULL_SEMESTER_WIPE)
                .confirmationPhrase("ОЧИСТИТЬ КУРС")
                .build();

        ResetResultDto result = resetService.resetSemester(request, adminUser);

        // Verification of deleted records
        assertThat(result.getDeletedAttempts()).isEqualTo(15);
        assertThat(result.getDeletedQuizAttempts()).isEqualTo(8);
        assertThat(result.getDeletedLabRecords()).isEqualTo(12); // 4 + 6 + 2
        assertThat(result.getDeletedStudents()).isEqualTo(3);
        assertThat(result.getDeletedUsers()).isEqualTo(1); // Only studentUser, admin & teacher preserved!
        assertThat(result.getDeletedGroups()).isEqualTo(3);
        assertThat(result.getTimestamp()).isNotNull();

        // Verify student user was deleted
        verify(userRepository).delete(studentUser);
        // CRITICAL: verify admin and instructor users were NEVER deleted
        verify(userRepository, never()).delete(adminUser);
        verify(userRepository, never()).delete(instructorUser);
        // CRITICAL: verify CTF tasks were NEVER touched
        verifyNoInteractions(ctfTaskRepository);

        // Verify all progress repositories were invoked
        verify(attemptRepository).deleteAllAttempts();
        verify(quizAttemptRepository).deleteAllQuizAttempts();
        verify(promoCodeClaimRepository).deleteAllClaims();
        verify(promoCodeRepository).resetAllUsedCounts();
        verify(scoreAdjustmentRepository).deleteAllAdjustments();
        verify(studentRepository).deleteAllManualSubmissions();
        verify(attendanceRepository).deleteAllAttendance();
        verify(studentRepository).deleteAllHiddenStudents();

        // Verify groups and flows deleted in full platform wipe
        verify(groupRepository).deleteAllGroups();
        verify(flowRepository).deleteAllFlows();

        // Verify audit log and leaderboard broadcast
        verify(auditService).logEvent(
                eq(adminUserId),
                eq("admin"),
                eq("ADMIN"),
                eq("ACADEMIC_SEMESTER_RESET"),
                eq("PLATFORM"),
                any(),
                eq("SUCCESS"),
                any()
        );
        verify(leaderboardService).broadcastUpdate();
    }

    @Test
    @DisplayName("3. В режиме PROGRESS_ONLY студенты, пользователи и группы сохраняются")
    void testProgressOnlyKeepsStudentsAndGroups() {
        Student student1 = Student.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .build();

        when(studentRepository.findAll()).thenReturn(List.of(student1));
        when(attemptRepository.deleteAllAttempts()).thenReturn(5);
        when(quizAttemptRepository.deleteAllQuizAttempts()).thenReturn(3);
        when(labScoreHistoryRepository.deleteAllHistory()).thenReturn(1);
        when(studentLabRepository.deleteAllStudentLabs()).thenReturn(2);
        when(labScoreRepository.deleteAllScores()).thenReturn(1);
        when(attendanceRepository.deleteAllAttendance()).thenReturn(7);
        when(studentRepository.resetAllGradebookFields()).thenReturn(1);

        ResetRequestDto request = ResetRequestDto.builder()
                .mode(ResetMode.PROGRESS_ONLY)
                .confirmationPhrase("RESET")
                .build();

        ResetResultDto result = resetService.resetSemester(request, adminUser);

        assertThat(result.getDeletedAttempts()).isEqualTo(5);
        assertThat(result.getDeletedQuizAttempts()).isEqualTo(3);
        assertThat(result.getDeletedLabRecords()).isEqualTo(4); // 1 + 2 + 1
        assertThat(result.getDeletedAttendanceRecords()).isEqualTo(7);
        assertThat(result.getResetControlPointRecords()).isEqualTo(1);
        assertThat(result.getDeletedStudents()).isEqualTo(0);
        assertThat(result.getDeletedUsers()).isEqualTo(0);
        assertThat(result.getDeletedGroups()).isEqualTo(0);

        // Verify progress deleted
        verify(attendanceRepository).deleteAllAttendance();
        verify(promoCodeClaimRepository).deleteAllClaims();
        verify(scoreAdjustmentRepository).deleteAllAdjustments();
        verify(studentRepository).deleteAllManualSubmissions();
        verify(studentRepository).resetAllGradebookFields();

        // Verify no students or users were deleted
        verify(studentRepository, never()).deleteAllStudents();
        verify(studentRepository, never()).deleteStudentsByIdIn(any());
        verify(userRepository, never()).delete(any());
        verify(groupRepository, never()).deleteAllGroups();
        verify(flowRepository, never()).deleteAllFlows();

        verify(leaderboardService).broadcastUpdate();
        verify(auditService).logEvent(any(), any(), any(), any(), eq("PLATFORM"), any(), eq("SUCCESS"), any());
    }

    @Test
    @DisplayName("4. Фильтрация по конкретной группе затрагивает только студентов этой группы")
    void testScopedResetByGroupId() {
        UUID groupId = UUID.randomUUID();
        AcademicGroup group = AcademicGroup.builder().id(groupId).name("ИУ7-51Б").build();

        UUID studentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).userId(userId).academicGroup(group).build();
        User normalUser = User.builder().id(userId).role(Role.STUDENT).build();

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(studentRepository.findByAcademicGroupId(groupId)).thenReturn(List.of(student));
        when(userRepository.findById(userId)).thenReturn(Optional.of(normalUser));
        when(studentRepository.deleteStudentsByIdIn(List.of(studentId))).thenReturn(1);
        when(attemptRepository.deleteByStudentIdIn(List.of(studentId))).thenReturn(3);

        ResetRequestDto request = ResetRequestDto.builder()
                .mode(ResetMode.FULL_SEMESTER_WIPE)
                .groupId(groupId)
                .confirmationPhrase("ОЧИСТИТЬ КУРС")
                .build();

        ResetResultDto result = resetService.resetSemester(request, adminUser);

        assertThat(result.getDeletedStudents()).isEqualTo(1);
        assertThat(result.getDeletedUsers()).isEqualTo(1);
        assertThat(result.getDeletedGroups()).isEqualTo(0); // Specific group is NOT deleted

        verify(attemptRepository).deleteByStudentIdIn(List.of(studentId));
        verify(studentRepository).deleteStudentsByIdIn(List.of(studentId));
        verify(userRepository).delete(normalUser);
        verify(attendanceRepository).deleteByStudentIdIn(List.of(studentId));
        verify(promoCodeClaimRepository).deleteByStudentIdIn(List.of(studentId));
        verify(scoreAdjustmentRepository).deleteByStudentIdIn(List.of(studentId));
        verify(studentRepository).deleteManualSubmissionsByStudentIdIn(List.of(studentId));
        verify(promoCodeRepository).recalculateUsedCounts();
        verify(groupRepository, never()).deleteAllGroups();
        verify(flowRepository, never()).deleteAllFlows();
    }

    @Test
    @DisplayName("5. Фильтрация по конкретному потоку удаляет группы и сам поток в режиме FULL_SEMESTER_WIPE")
    void testScopedResetByStreamId() {
        UUID streamId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).userId(userId).build();
        User studentUser = User.builder().id(userId).role(Role.STUDENT).build();

        when(flowRepository.existsById(streamId)).thenReturn(true);
        when(studentRepository.findByAcademicFlowId(streamId)).thenReturn(List.of(student));
        when(userRepository.findById(userId)).thenReturn(Optional.of(studentUser));
        when(studentRepository.deleteStudentsByIdIn(List.of(studentId))).thenReturn(1);
        when(groupRepository.deleteByAcademicFlowId(streamId)).thenReturn(2);

        ResetRequestDto request = ResetRequestDto.builder()
                .mode(ResetMode.FULL_SEMESTER_WIPE)
                .streamId(streamId)
                .confirmationPhrase("ОЧИСТИТЬ КУРС")
                .build();

        ResetResultDto result = resetService.resetSemester(request, adminUser);

        assertThat(result.getDeletedStudents()).isEqualTo(1);
        assertThat(result.getDeletedUsers()).isEqualTo(1);
        assertThat(result.getDeletedGroups()).isEqualTo(2);

        verify(groupRepository).deleteByAcademicFlowId(streamId);
        verify(flowRepository).deleteById(streamId);
        verify(groupRepository, never()).deleteAllGroups();
        verify(flowRepository, never()).deleteAllFlows();
    }

    @Test
    @DisplayName("6. При несовпадении потока и группы выбрасывается исключение")
    void testGroupStreamMismatchThrowsException() {
        UUID streamA = UUID.randomUUID();
        UUID streamB = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        AcademicFlow flowA = AcademicFlow.builder().id(streamA).name("Поток A").build();
        AcademicGroup group = AcademicGroup.builder().id(groupId).academicFlow(flowA).build();

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

        ResetRequestDto request = ResetRequestDto.builder()
                .mode(ResetMode.PROGRESS_ONLY)
                .streamId(streamB)
                .groupId(groupId)
                .confirmationPhrase("RESET")
                .build();

        assertThatThrownBy(() -> resetService.resetSemester(request, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Группа не принадлежит указанному потоку");
    }

    @Test
    @DisplayName("7. При несуществующем потоке выбрасывается исключение")
    void testNonExistentStreamThrowsException() {
        UUID streamId = UUID.randomUUID();
        when(flowRepository.existsById(streamId)).thenReturn(false);

        ResetRequestDto request = ResetRequestDto.builder()
                .mode(ResetMode.PROGRESS_ONLY)
                .streamId(streamId)
                .confirmationPhrase("RESET")
                .build();

        assertThatThrownBy(() -> resetService.resetSemester(request, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Поток не найден");
    }

    @Test
    @DisplayName("8. Батчинг больших списков студентов (больше 500) корректно разбивает запросы")
    void testLargeBatchOfStudentsPartitioning() {
        UUID groupId = UUID.randomUUID();
        AcademicGroup group = AcademicGroup.builder().id(groupId).name("Большая группа").build();

        List<Student> students = new java.util.ArrayList<>();
        for (int i = 0; i < 1100; i++) {
            students.add(Student.builder().id(UUID.randomUUID()).build());
        }

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(studentRepository.findByAcademicGroupId(groupId)).thenReturn(students);

        ResetRequestDto request = ResetRequestDto.builder()
                .mode(ResetMode.PROGRESS_ONLY)
                .groupId(groupId)
                .confirmationPhrase("ОЧИСТИТЬ КУРС")
                .build();

        resetService.resetSemester(request, adminUser);

        // 1100 students partitioned into chunks of 500 -> 3 batches (500, 500, 100)
        verify(attemptRepository, org.mockito.Mockito.times(3)).deleteByStudentIdIn(any());
        verify(attendanceRepository, org.mockito.Mockito.times(3)).deleteByStudentIdIn(any());
    }
}
