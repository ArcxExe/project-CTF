package com.arcx.ctfplatform.tests.service;

import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.entity.StudentStatus;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.tests.dto.QuestionAnswerDTO;
import com.arcx.ctfplatform.tests.dto.QuizOptionRequest;
import com.arcx.ctfplatform.tests.dto.QuizOptionUpdateRequest;
import com.arcx.ctfplatform.tests.dto.QuizQuestionRequest;
import com.arcx.ctfplatform.tests.entity.QuizAttempt;
import com.arcx.ctfplatform.tests.entity.QuizAttemptStatus;
import com.arcx.ctfplatform.tests.entity.QuizOption;
import com.arcx.ctfplatform.tests.entity.QuizQuestion;
import com.arcx.ctfplatform.tests.entity.Test;
import com.arcx.ctfplatform.tests.entity.TestStatus;
import com.arcx.ctfplatform.tests.repository.QuizAttemptRepository;
import com.arcx.ctfplatform.tests.repository.QuizOptionRepository;
import com.arcx.ctfplatform.tests.repository.QuizQuestionRepository;
import com.arcx.ctfplatform.tests.repository.TestRepository;
import com.arcx.ctfplatform.users.entity.Role;
import com.arcx.ctfplatform.users.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock
    private QuizAttemptRepository attemptRepository;
    @Mock
    private QuizQuestionRepository questionRepository;
    @Mock
    private QuizOptionRepository optionRepository;
    @Mock
    private TestRepository testRepository;
    @Mock
    private StudentRepository studentRepository;

    private QuizService quizService;

    private UUID userId;
    private UUID studentId;
    private UUID quizId;
    private User studentUser;
    private Student student;
    private Test publishedTest;

    @BeforeEach
    void setUp() {
        quizService = new QuizService(
                attemptRepository,
                questionRepository,
                optionRepository,
                testRepository,
                studentRepository
        );
        userId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        quizId = UUID.randomUUID();
        studentUser = User.builder().id(userId).role(Role.STUDENT).build();
        student = Student.builder()
                .id(studentId)
                .userId(userId)
                .studentCode("STU-01")
                .status(StudentStatus.ACTIVE)
                .build();
        publishedTest = Test.builder()
                .id(quizId)
                .competitionId(UUID.randomUUID())
                .title("Входной тест")
                .status(TestStatus.PUBLISHED)
                .timeLimitMinutes(30)
                .build();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("BUG-001: Starting a published quiz creates an IN_PROGRESS attempt and unlocks questions")
    void startPublishedQuizAndFetchQuestions() {
        UUID questionId = UUID.randomUUID();
        QuizQuestion question = QuizQuestion.builder()
                .id(questionId)
                .testId(quizId)
                .type("RADIO")
                .text("Вопрос 1")
                .points(5)
                .ordering(1)
                .build();
        QuizOption option = QuizOption.builder()
                .id(UUID.randomUUID())
                .questionId(questionId)
                .optionText("Ответ A")
                .isCorrect(true)
                .build();

        when(studentRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(student));
        when(testRepository.findById(quizId)).thenReturn(Optional.of(publishedTest));
        when(attemptRepository.findAllByQuizIdAndStudentId(quizId, studentId)).thenReturn(List.of());
        when(attemptRepository.save(any(QuizAttempt.class))).thenAnswer(inv -> {
            QuizAttempt a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        QuizAttempt started = quizService.startQuiz(quizId, userId);
        assertThat(started.getStatus()).isEqualTo(QuizAttemptStatus.IN_PROGRESS);
        assertThat(started.getQuizId()).isEqualTo(quizId);
        assertThat(started.getStudentId()).isEqualTo(studentId);

        when(studentRepository.findByUserId(userId)).thenReturn(Optional.of(student));
        when(attemptRepository.findAllByQuizIdAndStudentId(quizId, studentId)).thenReturn(List.of(started));
        when(questionRepository.findAllByTestIdOrderByOrderingAsc(quizId)).thenReturn(List.of(question));
        when(optionRepository.findAllByQuestionIdIn(List.of(questionId))).thenReturn(List.of(option));

        List<Map<String, Object>> questions = quizService.getQuestionsForCaller(quizId, studentUser);
        assertThat(questions).hasSize(1);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("BUG-001: Resuming an active IN_PROGRESS attempt returns the existing attempt")
    void resumeExistingInProgressAttempt() {
        QuizAttempt existing = QuizAttempt.builder()
                .id(UUID.randomUUID())
                .quizId(quizId)
                .studentId(studentId)
                .startedAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .status(QuizAttemptStatus.IN_PROGRESS)
                .score(0)
                .build();

        when(studentRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(student));
        when(testRepository.findById(quizId)).thenReturn(Optional.of(publishedTest));
        when(attemptRepository.findAllByQuizIdAndStudentId(quizId, studentId)).thenReturn(List.of(existing));

        QuizAttempt resumed = quizService.startQuiz(quizId, userId);
        assertThat(resumed.getId()).isEqualTo(existing.getId());
        assertThat(resumed.getStatus()).isEqualTo(QuizAttemptStatus.IN_PROGRESS);
        verify(attemptRepository, never()).save(any());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("BUG-001: Expired IN_PROGRESS attempt is auto-completed when resuming")
    void expiredInProgressAttemptIsAutoCompletedOnResume() {
        QuizAttempt expired = QuizAttempt.builder()
                .id(UUID.randomUUID())
                .quizId(quizId)
                .studentId(studentId)
                .startedAt(Instant.now().minus(40, ChronoUnit.MINUTES))
                .status(QuizAttemptStatus.IN_PROGRESS)
                .score(0)
                .build();

        when(studentRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(student));
        when(testRepository.findById(quizId)).thenReturn(Optional.of(publishedTest));
        when(attemptRepository.findAllByQuizIdAndStudentId(quizId, studentId)).thenReturn(List.of(expired));
        when(attemptRepository.save(any(QuizAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        QuizAttempt result = quizService.startQuiz(quizId, userId);
        assertThat(result.getStatus()).isEqualTo(QuizAttemptStatus.COMPLETED);
        assertThat(result.getCompletedAt()).isNotNull();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("BUG-001 & BUG-011: Pending or blocked student gets clear Russian message")
    void pendingOrBlockedStudentGetsClearRussianMessage() {
        student.setStatus(StudentStatus.PENDING_BINDING_VERIFICATION);
        when(studentRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> quizService.startQuiz(quizId, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ожидает подтверждения преподавателем");

        student.setStatus(StudentStatus.BLOCKED);
        assertThatThrownBy(() -> quizService.startQuiz(quizId, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("заблокирована или дисквалифицирована");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("BUG-001 & BUG-011: Fetching questions without active attempt returns Russian error")
    void getQuestionsWithoutAttemptThrowsRussianError() {
        when(testRepository.findById(quizId)).thenReturn(Optional.of(publishedTest));
        when(studentRepository.findByUserId(userId)).thenReturn(Optional.of(student));
        when(attemptRepository.findAllByQuizIdAndStudentId(quizId, studentId)).thenReturn(List.of());

        assertThatThrownBy(() -> quizService.getQuestionsForCaller(quizId, studentUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Для просмотра вопросов необходимо начать попытку прохождения теста");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Issue #20: startQuiz populates serverNow and remainingSeconds for timed and untimed tests")
    void startQuizPopulatesServerTimingMetadata() {
        when(studentRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(student));
        when(testRepository.findById(quizId)).thenReturn(Optional.of(publishedTest));
        when(attemptRepository.findAllByQuizIdAndStudentId(quizId, studentId)).thenReturn(List.of());
        when(attemptRepository.save(any(QuizAttempt.class))).thenAnswer(inv -> {
            QuizAttempt a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        QuizAttempt started = quizService.startQuiz(quizId, userId);
        assertThat(started.getServerNow()).isNotNull();
        assertThat(started.getRemainingSeconds()).isBetween(1790L, 1800L);

        UUID untimedQuizId = UUID.randomUUID();
        Test untimedTest = Test.builder()
                .id(untimedQuizId)
                .title("Бессрочный тест")
                .status(TestStatus.PUBLISHED)
                .timeLimitMinutes(null)
                .build();
        when(testRepository.findById(untimedQuizId)).thenReturn(Optional.of(untimedTest));
        when(attemptRepository.findAllByQuizIdAndStudentId(untimedQuizId, studentId)).thenReturn(List.of());

        QuizAttempt untimedStarted = quizService.startQuiz(untimedQuizId, userId);
        assertThat(untimedStarted.getServerNow()).isNotNull();
        assertThat(untimedStarted.getRemainingSeconds()).isNull();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Issue #20: autoSubmitExpired never closes untimed tests (timeLimitMinutes null or <= 0)")
    void autoSubmitExpiredDoesNotCloseUntimedTests() {
        UUID untimedNullId = UUID.randomUUID();
        UUID untimedZeroId = UUID.randomUUID();
        Test untimedNull = Test.builder()
                .id(untimedNullId)
                .title("Untimed null")
                .status(TestStatus.PUBLISHED)
                .timeLimitMinutes(null)
                .build();
        Test untimedZero = Test.builder()
                .id(untimedZeroId)
                .title("Untimed zero")
                .status(TestStatus.PUBLISHED)
                .timeLimitMinutes(0)
                .build();

        QuizAttempt attempt1 = QuizAttempt.builder()
                .id(UUID.randomUUID())
                .quizId(untimedNullId)
                .studentId(studentId)
                .startedAt(Instant.now().minus(3, ChronoUnit.HOURS))
                .status(QuizAttemptStatus.IN_PROGRESS)
                .build();
        QuizAttempt attempt2 = QuizAttempt.builder()
                .id(UUID.randomUUID())
                .quizId(untimedZeroId)
                .studentId(studentId)
                .startedAt(Instant.now().minus(3, ChronoUnit.HOURS))
                .status(QuizAttemptStatus.IN_PROGRESS)
                .build();

        when(attemptRepository.findAllByStatus(QuizAttemptStatus.IN_PROGRESS)).thenReturn(List.of(attempt1, attempt2));
        when(testRepository.findById(untimedNullId)).thenReturn(Optional.of(untimedNull));
        when(testRepository.findById(untimedZeroId)).thenReturn(Optional.of(untimedZero));

        quizService.autoSubmitExpired();

        assertThat(attempt1.getStatus()).isEqualTo(QuizAttemptStatus.IN_PROGRESS);
        assertThat(attempt2.getStatus()).isEqualTo(QuizAttemptStatus.IN_PROGRESS);
        verify(attemptRepository, never()).save(any());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Issue #20: submitAnswers accepts answers within final grace window and cleanly closes heavily expired attempts")
    void submitAnswersGraceWindowAndLateClose() {
        UUID qId = UUID.randomUUID();
        UUID optId = UUID.randomUUID();
        QuizQuestion question = QuizQuestion.builder()
                .id(qId)
                .testId(quizId)
                .type("RADIO")
                .text("Q1")
                .points(10)
                .ordering(1)
                .build();
        QuizOption correctOpt = QuizOption.builder()
                .id(optId)
                .questionId(qId)
                .optionText("Yes")
                .isCorrect(true)
                .build();

        // 30m limit + 60s late -> still inside 90s submit grace window -> answers graded!
        QuizAttempt slightlyLateAttempt = QuizAttempt.builder()
                .id(UUID.randomUUID())
                .quizId(quizId)
                .studentId(studentId)
                .startedAt(Instant.now().minus(31, ChronoUnit.MINUTES))
                .status(QuizAttemptStatus.IN_PROGRESS)
                .build();

        when(studentRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(student));
        when(attemptRepository.findAllByQuizIdAndStudentId(quizId, studentId))
                .thenReturn(List.of(slightlyLateAttempt));
        when(testRepository.findById(quizId)).thenReturn(Optional.of(publishedTest));
        when(questionRepository.findAllByTestIdOrderByOrderingAsc(quizId)).thenReturn(List.of(question));
        when(optionRepository.findAllByQuestionIdIn(List.of(qId))).thenReturn(List.of(correctOpt));
        when(attemptRepository.save(any(QuizAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        QuizAttempt graded = quizService.submitAnswers(
                quizId,
                userId,
                List.of(new QuestionAnswerDTO(qId, List.of(optId.toString())))
        );
        assertThat(graded.getStatus()).isEqualTo(QuizAttemptStatus.COMPLETED);
        assertThat(graded.getScore()).isEqualTo(10.0);
        assertThat(graded.getRemainingSeconds()).isEqualTo(0L);

        // 45m elapsed on 30m test -> beyond grace window -> cleanly closed as COMPLETED with score 0
        QuizAttempt veryLateAttempt = QuizAttempt.builder()
                .id(UUID.randomUUID())
                .quizId(quizId)
                .studentId(studentId)
                .startedAt(Instant.now().minus(45, ChronoUnit.MINUTES))
                .status(QuizAttemptStatus.IN_PROGRESS)
                .build();
        when(attemptRepository.findAllByQuizIdAndStudentId(quizId, studentId))
                .thenReturn(List.of(veryLateAttempt));

        QuizAttempt closedExpired = quizService.submitAnswers(
                quizId,
                userId,
                List.of(new QuestionAnswerDTO(qId, List.of(optId.toString())))
        );
        assertThat(closedExpired.getStatus()).isEqualTo(QuizAttemptStatus.COMPLETED);
        assertThat(closedExpired.getScore()).isEqualTo(0.0);
        assertThat(closedExpired.getCompletedAt()).isNotNull();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Issue #19: startQuiz acquires pessimistic lock on student")
    void startQuizAcquiresPessimisticLockOnStudent() {
        when(studentRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(student));
        when(testRepository.findById(quizId)).thenReturn(Optional.of(publishedTest));
        when(attemptRepository.findAllByQuizIdAndStudentId(quizId, studentId)).thenReturn(List.of());
        when(attemptRepository.save(any(QuizAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        quizService.startQuiz(quizId, userId);

        verify(studentRepository).findByUserIdWithLock(userId);
        verify(studentRepository, never()).findByUserId(any());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Issue #19: startQuiz deduplicates multiple IN_PROGRESS attempts and returns earliest")
    void startQuizWithDuplicateInProgressReturnsEarliestAndDeletesDuplicate() {
        QuizAttempt later = QuizAttempt.builder()
                .id(UUID.randomUUID())
                .quizId(quizId)
                .studentId(studentId)
                .startedAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                .status(QuizAttemptStatus.IN_PROGRESS)
                .score(0)
                .build();
        QuizAttempt earliest = QuizAttempt.builder()
                .id(UUID.randomUUID())
                .quizId(quizId)
                .studentId(studentId)
                .startedAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .status(QuizAttemptStatus.IN_PROGRESS)
                .score(0)
                .build();

        when(studentRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(student));
        when(testRepository.findById(quizId)).thenReturn(Optional.of(publishedTest));
        when(attemptRepository.findAllByQuizIdAndStudentId(quizId, studentId)).thenReturn(List.of(later, earliest));

        QuizAttempt resumed = quizService.startQuiz(quizId, userId);

        assertThat(resumed.getId()).isEqualTo(earliest.getId());
        verify(attemptRepository).deleteAll(List.of(later));
        verify(attemptRepository, never()).save(any());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Issue #19: startQuiz rejects if a COMPLETED attempt exists alongside stray IN_PROGRESS")
    void startQuizWithCompletedAndStrayInProgressThrowsAlreadyCompleted() {
        QuizAttempt inProgress = QuizAttempt.builder()
                .id(UUID.randomUUID())
                .quizId(quizId)
                .studentId(studentId)
                .startedAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .status(QuizAttemptStatus.IN_PROGRESS)
                .score(0)
                .build();
        QuizAttempt completed = QuizAttempt.builder()
                .id(UUID.randomUUID())
                .quizId(quizId)
                .studentId(studentId)
                .startedAt(Instant.now().minus(10, ChronoUnit.MINUTES))
                .completedAt(Instant.now().minus(2, ChronoUnit.MINUTES))
                .status(QuizAttemptStatus.COMPLETED)
                .score(10)
                .build();

        when(studentRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(student));
        when(testRepository.findById(quizId)).thenReturn(Optional.of(publishedTest));
        when(attemptRepository.findAllByQuizIdAndStudentId(quizId, studentId)).thenReturn(List.of(inProgress, completed));

        assertThatThrownBy(() -> quizService.startQuiz(quizId, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Вы уже прошли этот тест");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Issue #19: getQuestionsForCaller handles duplicate IN_PROGRESS attempts without NonUniqueResult")
    void getQuestionsForCallerHandlesDuplicateInProgressAttemptsWithoutError() {
        QuizAttempt attempt1 = QuizAttempt.builder()
                .id(UUID.randomUUID())
                .quizId(quizId)
                .studentId(studentId)
                .startedAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .status(QuizAttemptStatus.IN_PROGRESS)
                .build();
        QuizAttempt attempt2 = QuizAttempt.builder()
                .id(UUID.randomUUID())
                .quizId(quizId)
                .studentId(studentId)
                .startedAt(Instant.now().minus(4, ChronoUnit.MINUTES))
                .status(QuizAttemptStatus.IN_PROGRESS)
                .build();
        UUID questionId = UUID.randomUUID();
        QuizQuestion question = QuizQuestion.builder()
                .id(questionId)
                .testId(quizId)
                .type("RADIO")
                .text("Вопрос 1")
                .points(5)
                .ordering(1)
                .build();

        when(testRepository.findById(quizId)).thenReturn(Optional.of(publishedTest));
        when(studentRepository.findByUserId(userId)).thenReturn(Optional.of(student));
        when(attemptRepository.findAllByQuizIdAndStudentId(quizId, studentId)).thenReturn(List.of(attempt1, attempt2));
        when(questionRepository.findAllByTestIdOrderByOrderingAsc(quizId)).thenReturn(List.of(question));
        when(optionRepository.findAllByQuestionIdIn(List.of(questionId))).thenReturn(List.of());

        List<Map<String, Object>> result = quizService.getQuestionsForCaller(quizId, studentUser);

        assertThat(result).hasSize(1);
        verify(attemptRepository, never()).findByQuizIdAndStudentIdAndStatus(any(), any(), any());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Issue #19: submitAnswers locks student and deduplicates multiple IN_PROGRESS attempts")
    void submitAnswersLocksStudentAndHandlesDuplicateInProgressAttempts() {
        QuizAttempt later = QuizAttempt.builder()
                .id(UUID.randomUUID())
                .quizId(quizId)
                .studentId(studentId)
                .startedAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                .status(QuizAttemptStatus.IN_PROGRESS)
                .score(0)
                .build();
        QuizAttempt earliest = QuizAttempt.builder()
                .id(UUID.randomUUID())
                .quizId(quizId)
                .studentId(studentId)
                .startedAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .status(QuizAttemptStatus.IN_PROGRESS)
                .score(0)
                .build();
        UUID questionId = UUID.randomUUID();
        UUID correctOptionId = UUID.randomUUID();
        QuizQuestion question = QuizQuestion.builder()
                .id(questionId)
                .testId(quizId)
                .type("RADIO")
                .text("Вопрос 1")
                .points(10)
                .ordering(1)
                .build();
        QuizOption correctOption = QuizOption.builder()
                .id(correctOptionId)
                .questionId(questionId)
                .optionText("Верно")
                .isCorrect(true)
                .build();

        when(studentRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(student));
        when(attemptRepository.findAllByQuizIdAndStudentId(quizId, studentId)).thenReturn(List.of(later, earliest));
        when(testRepository.findById(quizId)).thenReturn(Optional.of(publishedTest));
        when(questionRepository.findAllByTestIdOrderByOrderingAsc(quizId)).thenReturn(List.of(question));
        when(optionRepository.findAllByQuestionIdIn(List.of(questionId))).thenReturn(List.of(correctOption));
        when(attemptRepository.save(any(QuizAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        QuizAttempt submitted = quizService.submitAnswers(
                quizId,
                userId,
                List.of(new QuestionAnswerDTO(questionId, List.of(correctOptionId.toString())))
        );

        assertThat(submitted.getId()).isEqualTo(earliest.getId());
        assertThat(submitted.getStatus()).isEqualTo(QuizAttemptStatus.COMPLETED);
        assertThat(submitted.getScore()).isEqualTo(10.0);
        verify(studentRepository).findByUserIdWithLock(userId);
        verify(attemptRepository).deleteAll(List.of(later));
        verify(attemptRepository, never()).findByQuizIdAndStudentIdAndStatus(any(), any(), any());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Issue #21: SEQUENCE options are deterministically shuffled for students and unshuffled for admins")
    void sequenceOptionsAreShuffledForStudentsAndDeterministicAcrossReloads() {
        UUID questionId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        QuizAttempt activeAttempt = QuizAttempt.builder()
                .id(attemptId)
                .quizId(quizId)
                .studentId(studentId)
                .startedAt(Instant.now().minus(2, ChronoUnit.MINUTES))
                .status(QuizAttemptStatus.IN_PROGRESS)
                .build();
        QuizQuestion seqQuestion = QuizQuestion.builder()
                .id(questionId)
                .testId(quizId)
                .type("SEQUENCE")
                .text("Расставьте шаги")
                .points(10)
                .ordering(1)
                .build();
        QuizOption o1 = QuizOption.builder().id(UUID.randomUUID()).questionId(questionId).optionText("Шаг 1").sequenceOrder(1).build();
        QuizOption o2 = QuizOption.builder().id(UUID.randomUUID()).questionId(questionId).optionText("Шаг 2").sequenceOrder(2).build();
        QuizOption o3 = QuizOption.builder().id(UUID.randomUUID()).questionId(questionId).optionText("Шаг 3").sequenceOrder(3).build();
        QuizOption o4 = QuizOption.builder().id(UUID.randomUUID()).questionId(questionId).optionText("Шаг 4").sequenceOrder(4).build();
        List<QuizOption> canonical = List.of(o1, o2, o3, o4);

        when(testRepository.findById(quizId)).thenReturn(Optional.of(publishedTest));
        when(studentRepository.findByUserId(userId)).thenReturn(Optional.of(student));
        when(attemptRepository.findAllByQuizIdAndStudentId(quizId, studentId)).thenReturn(List.of(activeAttempt));
        when(questionRepository.findAllByTestIdOrderByOrderingAsc(quizId)).thenReturn(List.of(seqQuestion));
        when(optionRepository.findAllByQuestionIdIn(List.of(questionId))).thenReturn(canonical);

        @SuppressWarnings("unchecked")
        List<QuizOption> firstCall = (List<QuizOption>) quizService.getQuestionsForCaller(quizId, studentUser).get(0).get("options");
        @SuppressWarnings("unchecked")
        List<QuizOption> secondCall = (List<QuizOption>) quizService.getQuestionsForCaller(quizId, studentUser).get(0).get("options");

        assertThat(firstCall).containsExactlyInAnyOrderElementsOf(canonical);
        assertThat(firstCall).isNotEqualTo(canonical);
        assertThat(secondCall).containsExactlyElementsOf(firstCall);

        User adminUser = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).build();
        @SuppressWarnings("unchecked")
        List<QuizOption> adminCall = (List<QuizOption>) quizService.getQuestionsForCaller(quizId, adminUser).get(0).get("options");
        assertThat(adminCall).containsExactlyElementsOf(canonical);

        QuizOption a = QuizOption.builder().id(UUID.randomUUID()).questionId(questionId).optionText("A").sequenceOrder(1).build();
        QuizOption b = QuizOption.builder().id(UUID.randomUUID()).questionId(questionId).optionText("B").sequenceOrder(2).build();
        when(optionRepository.findAllByQuestionIdIn(List.of(questionId))).thenReturn(List.of(a, b));
        @SuppressWarnings("unchecked")
        List<QuizOption> twoOptionCall = (List<QuizOption>) quizService.getQuestionsForCaller(quizId, studentUser).get(0).get("options");
        assertThat(twoOptionCall).containsExactly(b, a);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Issue #21: Adding a correct option to RADIO resets previous correct option")
    void addOptionResetsPreviousCorrectForRadioQuestion() {
        UUID questionId = UUID.randomUUID();
        QuizQuestion radioQuestion = QuizQuestion.builder()
                .id(questionId)
                .testId(quizId)
                .type("RADIO")
                .text("Один верный")
                .points(5)
                .ordering(1)
                .build();
        QuizOption oldCorrect = QuizOption.builder()
                .id(UUID.randomUUID())
                .questionId(questionId)
                .optionText("Старый верный")
                .isCorrect(true)
                .sequenceOrder(1)
                .build();
        QuizOption oldWrong = QuizOption.builder()
                .id(UUID.randomUUID())
                .questionId(questionId)
                .optionText("Неверный")
                .isCorrect(false)
                .sequenceOrder(2)
                .build();

        when(questionRepository.findById(questionId)).thenReturn(Optional.of(radioQuestion));
        when(optionRepository.findAllByQuestionIdOrderBySequenceOrderAsc(questionId)).thenReturn(List.of(oldCorrect, oldWrong));
        when(optionRepository.save(any(QuizOption.class))).thenAnswer(inv -> {
            QuizOption saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        QuizOption created = quizService.addOption(questionId, new QuizOptionRequest("Новый верный", true, true, 3));

        assertThat(oldCorrect.isCorrect()).isFalse();
        assertThat(created.isCorrect()).isTrue();
        verify(optionRepository).save(oldCorrect);
        verify(optionRepository, never()).save(oldWrong);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Issue #21: Adding a correct option to CHECKBOX preserves existing correct options")
    void addOptionPreservesExistingCorrectForCheckboxQuestion() {
        UUID questionId = UUID.randomUUID();
        QuizQuestion checkboxQuestion = QuizQuestion.builder()
                .id(questionId)
                .testId(quizId)
                .type("CHECKBOX")
                .text("Несколько верных")
                .points(5)
                .ordering(1)
                .build();

        when(questionRepository.findById(questionId)).thenReturn(Optional.of(checkboxQuestion));
        when(optionRepository.save(any(QuizOption.class))).thenAnswer(inv -> inv.getArgument(0));

        QuizOption created = quizService.addOption(questionId, new QuizOptionRequest("Второй верный", true, null, 2));

        assertThat(created.isCorrect()).isTrue();
        verify(optionRepository, never()).findAllByQuestionIdOrderBySequenceOrderAsc(any());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Issue #21: updateOption setting isCorrect=true on RADIO resets other options")
    void updateOptionResetsOtherCorrectOptionsForRadio() {
        UUID questionId = UUID.randomUUID();
        UUID opt1Id = UUID.randomUUID();
        UUID opt2Id = UUID.randomUUID();
        QuizQuestion radioQuestion = QuizQuestion.builder()
                .id(questionId)
                .testId(quizId)
                .type("RADIO")
                .text("Один верный")
                .points(5)
                .ordering(1)
                .build();
        QuizOption opt1 = QuizOption.builder().id(opt1Id).questionId(questionId).optionText("A").isCorrect(true).build();
        QuizOption opt2 = QuizOption.builder().id(opt2Id).questionId(questionId).optionText("B").isCorrect(false).build();

        when(optionRepository.findById(opt2Id)).thenReturn(Optional.of(opt2));
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(radioQuestion));
        when(optionRepository.findAllByQuestionIdOrderBySequenceOrderAsc(questionId)).thenReturn(List.of(opt1, opt2));
        when(optionRepository.save(any(QuizOption.class))).thenAnswer(inv -> inv.getArgument(0));

        QuizOption updated = quizService.updateOption(opt2Id, new QuizOptionUpdateRequest(null, true, null, null, null));

        assertThat(updated.isCorrect()).isTrue();
        assertThat(opt1.isCorrect()).isFalse();
        verify(optionRepository).save(opt1);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Issue #21: addQuestion validates payload and syncs Test.questionsCount on add and delete")
    void addAndDeleteQuestionValidatesAndSyncsQuestionsCount() {
        UUID q1Id = UUID.randomUUID();
        QuizQuestion savedQ1 = QuizQuestion.builder()
                .id(q1Id)
                .testId(quizId)
                .type("RADIO")
                .text("Вопрос 1")
                .points(5)
                .ordering(1)
                .build();

        when(testRepository.findById(quizId)).thenReturn(Optional.of(publishedTest));

        assertThatThrownBy(() -> quizService.addQuestion(quizId, new QuizQuestionRequest("INVALID", "Текст", 5, 1, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported question type");

        when(questionRepository.save(any(QuizQuestion.class))).thenReturn(savedQ1);
        when(questionRepository.findAllByTestIdOrderByOrderingAsc(quizId)).thenReturn(List.of(savedQ1));

        QuizQuestion created = quizService.addQuestion(quizId, new QuizQuestionRequest("radio", "Вопрос 1", 5, 1, null));
        assertThat(created.getType()).isEqualTo("RADIO");
        assertThat(publishedTest.getQuestionsCount()).isEqualTo(1);

        when(questionRepository.findById(q1Id)).thenReturn(Optional.of(savedQ1));
        when(questionRepository.findAllByTestIdOrderByOrderingAsc(quizId)).thenReturn(List.of());

        quizService.deleteQuestion(q1Id);
        assertThat(publishedTest.getQuestionsCount()).isEqualTo(0);
        verify(questionRepository).deleteById(q1Id);
    }
}
