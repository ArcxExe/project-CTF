package com.arcx.ctfplatform.attempts.service;

import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.entity.StudentStatus;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.attempts.dto.ChallengeSubmitResponse;
import com.arcx.ctfplatform.attempts.entity.Attempt;
import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.challenges.entity.CtfTask;
import com.arcx.ctfplatform.challenges.entity.TaskType;
import com.arcx.ctfplatform.challenges.repository.CtfTaskRepository;
import com.arcx.ctfplatform.common.storage.FileStorageService;
import com.arcx.ctfplatform.competitions.repository.CompetitionRepository;
import com.arcx.ctfplatform.leaderboard.service.LeaderboardService;
import com.arcx.ctfplatform.tests.entity.QuizAttempt;
import com.arcx.ctfplatform.tests.entity.QuizAttemptStatus;
import com.arcx.ctfplatform.tests.entity.TestStatus;
import com.arcx.ctfplatform.tests.repository.QuizAttemptRepository;
import com.arcx.ctfplatform.tests.repository.TestRepository;
import com.arcx.ctfplatform.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttemptServiceTest {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private CtfTaskRepository ctfTaskRepository;
    @Mock
    private AttemptRepository attemptRepository;
    @Mock
    private CompetitionRepository competitionRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LeaderboardService leaderboardService;
    @Mock
    private TestRepository testRepository;
    @Mock
    private QuizAttemptRepository quizAttemptRepository;

    private AttemptService attemptService;

    private final UUID userId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID taskId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        attemptService = new AttemptService(
                studentRepository,
                ctfTaskRepository,
                attemptRepository,
                competitionRepository,
                fileStorageService,
                userRepository,
                leaderboardService,
                testRepository,
                quizAttemptRepository
        );
    }

    @Test
    @DisplayName("submitFlag rejects RESERVE pool task")
    void testSubmitFlagRejectsReserveTask() {
        Student student = activeStudent();
        CtfTask reserveTask = flagTask("RESERVE");

        when(studentRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(student));
        when(ctfTaskRepository.findByIdWithLock(taskId)).thenReturn(Optional.of(reserveTask));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> attemptService.submitFlag(userId, taskId, "CTF{flag}")
        );

        assertEquals("Task is not available", ex.getMessage());
        verify(attemptRepository, never()).save(any());
    }

    @Test
    @DisplayName("submitFile rejects RESERVE pool task")
    void testSubmitFileRejectsReserveTask() {
        Student student = activeStudent();
        CtfTask reserveTask = new CtfTask();
        reserveTask.setId(taskId);
        reserveTask.setTitle("Reserve Upload");
        reserveTask.setTaskType(TaskType.FILE_UPLOAD);
        reserveTask.setPool("RESERVE");

        when(studentRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(student));
        when(ctfTaskRepository.findByIdWithLock(taskId)).thenReturn(Optional.of(reserveTask));

        MultipartFile file = mock(MultipartFile.class);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> attemptService.submitFile(userId, taskId, file)
        );

        assertEquals("Task is not available", ex.getMessage());
        verify(fileStorageService, never()).storeFile(any());
        verify(attemptRepository, never()).save(any());
    }

    @Test
    @DisplayName("submitFlag rejects task linked only to PUBLISHED test when student has no active QuizAttempt")
    void testSubmitFlagRequiresActiveQuizAttemptForTestTask() {
        Student student = activeStudent();
        CtfTask task = flagTask("MAIN");
        UUID testId = UUID.randomUUID();
        com.arcx.ctfplatform.tests.entity.Test publishedTest = com.arcx.ctfplatform.tests.entity.Test.builder()
                .id(testId)
                .title("KT1")
                .status(TestStatus.PUBLISHED)
                .timeLimitMinutes(30)
                .tasks(List.of(task))
                .build();

        when(studentRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(student));
        when(ctfTaskRepository.findByIdWithLock(taskId)).thenReturn(Optional.of(task));
        when(competitionRepository.findAll()).thenReturn(List.of());
        when(testRepository.findAllByStatus(TestStatus.PUBLISHED)).thenReturn(List.of(publishedTest));
        when(quizAttemptRepository.findAllByQuizIdAndStudentId(testId, studentId)).thenReturn(List.of());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> attemptService.submitFlag(userId, taskId, "CTF{flag}")
        );

        assertEquals("No active attempt found for this quiz", ex.getMessage());
        verify(attemptRepository, never()).save(any());
    }

    @Test
    @DisplayName("submitFlag rejects task linked only to PUBLISHED test when QuizAttempt is expired")
    void testSubmitFlagRejectsExpiredQuizAttemptForTestTask() {
        Student student = activeStudent();
        CtfTask task = flagTask("MAIN");
        UUID testId = UUID.randomUUID();
        com.arcx.ctfplatform.tests.entity.Test publishedTest = com.arcx.ctfplatform.tests.entity.Test.builder()
                .id(testId)
                .title("KT1")
                .status(TestStatus.PUBLISHED)
                .timeLimitMinutes(30)
                .tasks(List.of(task))
                .build();
        QuizAttempt expiredAttempt = QuizAttempt.builder()
                .id(UUID.randomUUID())
                .quizId(testId)
                .studentId(studentId)
                .startedAt(Instant.now().minus(45, ChronoUnit.MINUTES))
                .status(QuizAttemptStatus.IN_PROGRESS)
                .build();

        when(studentRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(student));
        when(ctfTaskRepository.findByIdWithLock(taskId)).thenReturn(Optional.of(task));
        when(competitionRepository.findAll()).thenReturn(List.of());
        when(testRepository.findAllByStatus(TestStatus.PUBLISHED)).thenReturn(List.of(publishedTest));
        when(quizAttemptRepository.findAllByQuizIdAndStudentId(testId, studentId)).thenReturn(List.of(expiredAttempt));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> attemptService.submitFlag(userId, taskId, "CTF{flag}")
        );

        assertEquals("Quiz time limit exceeded", ex.getMessage());
        verify(attemptRepository, never()).save(any());
    }

    @Test
    @DisplayName("submitFlag allows task linked to PUBLISHED test when student has active non-expired QuizAttempt")
    void testSubmitFlagAllowsActiveNonExpiredQuizAttemptForTestTask() {
        Student student = activeStudent();
        CtfTask task = flagTask("MAIN");
        UUID testId = UUID.randomUUID();
        com.arcx.ctfplatform.tests.entity.Test publishedTest = com.arcx.ctfplatform.tests.entity.Test.builder()
                .id(testId)
                .title("KT1")
                .status(TestStatus.PUBLISHED)
                .timeLimitMinutes(30)
                .tasks(List.of(task))
                .build();
        QuizAttempt activeAttempt = QuizAttempt.builder()
                .id(UUID.randomUUID())
                .quizId(testId)
                .studentId(studentId)
                .startedAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .status(QuizAttemptStatus.IN_PROGRESS)
                .build();

        when(studentRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(student));
        when(ctfTaskRepository.findByIdWithLock(taskId)).thenReturn(Optional.of(task));
        when(competitionRepository.findAll()).thenReturn(List.of());
        when(testRepository.findAllByStatus(TestStatus.PUBLISHED)).thenReturn(List.of(publishedTest));
        when(quizAttemptRepository.findAllByQuizIdAndStudentId(testId, studentId)).thenReturn(List.of(activeAttempt));
        when(attemptRepository.existsByTaskIdAndStudentIdAndIsCorrect(taskId, studentId, true)).thenReturn(false);
        when(attemptRepository.countByTaskIdAndIsCorrect(taskId, true)).thenReturn(0L);
        when(attemptRepository.existsByTaskIdAndIsCorrectTrue(taskId)).thenReturn(false);
        when(attemptRepository.countByTaskIdAndStudentIdAndIsCorrectFalseAndSubmittedAtAfter(eq(taskId), eq(studentId), any()))
                .thenReturn(0L);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        ChallengeSubmitResponse response = attemptService.submitFlag(userId, taskId, "CTF{flag}");

        assertTrue(response.isCorrect());
        verify(attemptRepository).save(any(Attempt.class));
    }

    private Student activeStudent() {
        return Student.builder()
                .id(studentId)
                .userId(userId)
                .studentCode("STU-200")
                .status(StudentStatus.ACTIVE)
                .build();
    }

    private CtfTask flagTask(String pool) {
        CtfTask task = new CtfTask();
        task.setId(taskId);
        task.setTitle("Test Task");
        task.setTaskType(TaskType.FLAG);
        task.setFlag("CTF{flag}");
        task.setBaseScore(100);
        task.setMaxScore(100);
        task.setPool(pool);
        return task;
    }
}
