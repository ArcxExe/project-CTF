package com.arcx.ctfplatform.tests.service;

import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.entity.StudentStatus;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
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

        when(studentRepository.findByUserId(userId)).thenReturn(Optional.of(student));
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

        when(attemptRepository.findByQuizIdAndStudentIdAndStatus(quizId, studentId, QuizAttemptStatus.IN_PROGRESS))
                .thenReturn(Optional.of(started));
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

        when(studentRepository.findByUserId(userId)).thenReturn(Optional.of(student));
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

        when(studentRepository.findByUserId(userId)).thenReturn(Optional.of(student));
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
        when(studentRepository.findByUserId(userId)).thenReturn(Optional.of(student));

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
        when(attemptRepository.findByQuizIdAndStudentIdAndStatus(quizId, studentId, QuizAttemptStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(attemptRepository.findAllByQuizIdAndStudentId(quizId, studentId)).thenReturn(List.of());

        assertThatThrownBy(() -> quizService.getQuestionsForCaller(quizId, studentUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Для просмотра вопросов необходимо начать попытку прохождения теста");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Issue #20: startQuiz populates serverNow and remainingSeconds for timed and untimed tests")
    void startQuizPopulatesServerTimingMetadata() {
        when(studentRepository.findByUserId(userId)).thenReturn(Optional.of(student));
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

        when(studentRepository.findByUserId(userId)).thenReturn(Optional.of(student));
        when(attemptRepository.findByQuizIdAndStudentIdAndStatus(quizId, studentId, QuizAttemptStatus.IN_PROGRESS))
                .thenReturn(Optional.of(slightlyLateAttempt));
        when(testRepository.findById(quizId)).thenReturn(Optional.of(publishedTest));
        when(questionRepository.findAllByTestIdOrderByOrderingAsc(quizId)).thenReturn(List.of(question));
        when(optionRepository.findAllByQuestionIdIn(List.of(qId))).thenReturn(List.of(correctOpt));
        when(attemptRepository.save(any(QuizAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        QuizAttempt graded = quizService.submitAnswers(
                quizId,
                userId,
                List.of(new com.arcx.ctfplatform.tests.dto.QuestionAnswerDTO(qId, List.of(optId.toString())))
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
        when(attemptRepository.findByQuizIdAndStudentIdAndStatus(quizId, studentId, QuizAttemptStatus.IN_PROGRESS))
                .thenReturn(Optional.of(veryLateAttempt));

        QuizAttempt closedExpired = quizService.submitAnswers(
                quizId,
                userId,
                List.of(new com.arcx.ctfplatform.tests.dto.QuestionAnswerDTO(qId, List.of(optId.toString())))
        );
        assertThat(closedExpired.getStatus()).isEqualTo(QuizAttemptStatus.COMPLETED);
        assertThat(closedExpired.getScore()).isEqualTo(0.0);
        assertThat(closedExpired.getCompletedAt()).isNotNull();
    }
}
