package com.arcx.ctfplatform.tests.service;

import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.entity.StudentStatus;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.audit.service.AuditService;
import com.arcx.ctfplatform.leaderboard.service.LeaderboardService;
import com.arcx.ctfplatform.tests.dto.QuestionAnswerDTO;
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
import com.arcx.ctfplatform.users.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QuizService {

    private final QuizAttemptRepository attemptRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizOptionRepository optionRepository;
    private final TestRepository testRepository;
    private final StudentRepository studentRepository;
    private final LeaderboardService leaderboardService;
    private final AuditService auditService;
    private final UserRepository userRepository;

    public QuizService(
            QuizAttemptRepository attemptRepository,
            QuizQuestionRepository questionRepository,
            QuizOptionRepository optionRepository,
            TestRepository testRepository,
            StudentRepository studentRepository
    ) {
        this(attemptRepository, questionRepository, optionRepository, testRepository, studentRepository, null, null, null);
    }

    @Autowired
    public QuizService(
            QuizAttemptRepository attemptRepository,
            QuizQuestionRepository questionRepository,
            QuizOptionRepository optionRepository,
            TestRepository testRepository,
            StudentRepository studentRepository,
            @Autowired(required = false) LeaderboardService leaderboardService,
            @Autowired(required = false) AuditService auditService,
            @Autowired(required = false) UserRepository userRepository
    ) {
        this.attemptRepository = attemptRepository;
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.testRepository = testRepository;
        this.studentRepository = studentRepository;
        this.leaderboardService = leaderboardService;
        this.auditService = auditService;
        this.userRepository = userRepository;
    }

    @Transactional
    public QuizAttempt startQuiz(UUID quizId, UUID userId) {
        Student student = studentRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        if (student.getStatus() == StudentStatus.PENDING_BINDING_VERIFICATION
                || student.getStatus() == StudentStatus.BLOCKED
                || student.getStatus() == StudentStatus.DISQUALIFIED) {
            throw new IllegalStateException("Student is not allowed to start quiz in status: " + student.getStatus());
        }
        
        Test test = testRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));

        if (test.getStatus() != TestStatus.PUBLISHED) {
            throw new IllegalStateException("Quiz is not published");
        }

        List<QuizAttempt> attempts = attemptRepository.findAllByQuizIdAndStudentId(quizId, student.getId());
        
        Optional<QuizAttempt> activeAttempt = attempts.stream()
                .filter(a -> a.getStatus() == QuizAttemptStatus.IN_PROGRESS)
                .findFirst();
        if (activeAttempt.isPresent()) {
            return activeAttempt.get();
        }

        boolean hasCompleted = attempts.stream()
                .anyMatch(a -> a.getStatus() == QuizAttemptStatus.COMPLETED);
        if (hasCompleted) {
            throw new IllegalStateException("Вы уже прошли этот тест");
        }

        QuizAttempt attempt = QuizAttempt.builder()
                .quizId(quizId)
                .studentId(student.getId())
                .startedAt(Instant.now())
                .status(QuizAttemptStatus.IN_PROGRESS)
                .score(0)
                .build();

        return attemptRepository.save(attempt);
    }

    @Transactional(readOnly = true)
    public List<QuizAttempt> getStudentAttempts(UUID userId) {
        Student student = studentRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        return attemptRepository.findAllByStudentId(student.getId());
    }

    @Transactional
    public QuizAttempt submitAnswers(UUID quizId, UUID userId, List<QuestionAnswerDTO> answers) {
        Student student = studentRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        QuizAttempt attempt = attemptRepository.findByQuizIdAndStudentIdAndStatus(quizId, student.getId(), QuizAttemptStatus.IN_PROGRESS)
                .orElseThrow(() -> new IllegalStateException("No active attempt found for this quiz"));

        Test test = testRepository.findById(attempt.getQuizId())
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));

        Instant now = Instant.now();
        if (test.getTimeLimitMinutes() != null && test.getTimeLimitMinutes() > 0 && attempt.getStartedAt() != null) {
            Instant deadline = attempt.getStartedAt()
                    .plus(test.getTimeLimitMinutes(), ChronoUnit.MINUTES)
                    .plusSeconds(30);
            if (now.isAfter(deadline)) {
                throw new IllegalStateException("Quiz time limit exceeded");
            }
        }
        
        List<QuizQuestion> questions = questionRepository.findAllByTestIdOrderByOrderingAsc(test.getId());
        List<UUID> questionIds = questions.stream().map(QuizQuestion::getId).collect(Collectors.toList());
        List<QuizOption> options = questionIds.isEmpty()
                ? Collections.emptyList()
                : optionRepository.findAllByQuestionIdIn(questionIds);

        Map<UUID, List<String>> answerMap = (answers == null ? Collections.<QuestionAnswerDTO>emptyList() : answers)
                .stream()
                .filter(Objects::nonNull)
                .filter(a -> a.questionId() != null)
                .collect(Collectors.toMap(
                        QuestionAnswerDTO::questionId,
                        a -> a.answers() != null ? a.answers() : Collections.emptyList(),
                        (a, b) -> b
                ));

        int totalScore = 0;

        for (QuizQuestion q : questions) {
            List<String> studentAnswer = answerMap.getOrDefault(q.getId(), Collections.emptyList());
            if (studentAnswer == null) {
                studentAnswer = Collections.emptyList();
            }
            int qPoints = q.getPoints() != null ? q.getPoints() : 0;
            List<QuizOption> qOptions = options.stream()
                    .filter(o -> o.getQuestionId().equals(q.getId()))
                    .collect(Collectors.toList());

            if ("SEQUENCE".equalsIgnoreCase(q.getType())) {
                List<String> correctSequence = qOptions.stream()
                        .sorted(Comparator.comparing(QuizOption::getSequenceOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(o -> o.getId().toString())
                        .collect(Collectors.toList());
                if (!correctSequence.isEmpty() && correctSequence.equals(studentAnswer)) {
                    totalScore += qPoints;
                }
            } else {
                List<String> correctOptionIds = qOptions.stream()
                        .filter(QuizOption::isCorrect)
                        .map(o -> o.getId().toString())
                        .collect(Collectors.toList());
                
                // For choice/multiple choice, exact match required
                if (!correctOptionIds.isEmpty() && new HashSet<>(correctOptionIds).equals(new HashSet<>(studentAnswer))) {
                    totalScore += qPoints;
                }
            }
        }

        attempt.setScore(totalScore);
        attempt.setCompletedAt(now);
        attempt.setStatus(QuizAttemptStatus.COMPLETED);

        QuizAttempt saved = attemptRepository.save(attempt);
        if (leaderboardService != null) {
            leaderboardService.broadcastUpdate();
        }
        if (auditService != null) {
            auditService.logEvent(
                    userId,
                    student.getStudentCode() != null ? student.getStudentCode() : userId.toString(),
                    "STUDENT",
                    "QUIZ_SUBMIT",
                    quizId.toString(),
                    "IN_PROGRESS",
                    "COMPLETED (score=" + totalScore + ")",
                    null
            );
        }
        return saved;
    }

    @Transactional
    public QuizQuestion updateQuestion(UUID questionId, QuizQuestion questionUpdates) {
        QuizQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found"));
        if (questionUpdates.getText() != null) question.setText(questionUpdates.getText());
        if (questionUpdates.getType() != null) question.setType(questionUpdates.getType());
        if (questionUpdates.getPoints() != null) question.setPoints(questionUpdates.getPoints());
        if (questionUpdates.getOrdering() != null) question.setOrdering(questionUpdates.getOrdering());
        return questionRepository.save(question);
    }

    @Transactional
    public void deleteQuestion(UUID questionId) {
        if (!questionRepository.existsById(questionId)) {
            throw new IllegalArgumentException("Question not found");
        }
        questionRepository.deleteById(questionId);
    }

    @Transactional
    public QuizOption updateOption(UUID optionId, QuizOption optionUpdates) {
        QuizOption option = optionRepository.findById(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Option not found"));
        if (optionUpdates.getOptionText() != null) option.setOptionText(optionUpdates.getOptionText());
        option.setCorrect(optionUpdates.isCorrect());
        if (optionUpdates.getSequenceOrder() != null) option.setSequenceOrder(optionUpdates.getSequenceOrder());
        return optionRepository.save(option);
    }

    @Transactional
    public void deleteOption(UUID optionId) {
        if (!optionRepository.existsById(optionId)) {
            throw new IllegalArgumentException("Option not found");
        }
        optionRepository.deleteById(optionId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getQuestionsForCaller(UUID testId, User user) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));

        if (!isAdminOrInstructor(user)) {
            if (test.getStatus() != TestStatus.PUBLISHED) {
                throw new IllegalStateException("Test is not published");
            }
            UUID resolvedUserId = resolveUserId(user);
            if (resolvedUserId == null) {
                throw new IllegalStateException("Active quiz attempt is required to view questions");
            }
            Student student = studentRepository.findByUserId(resolvedUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Student not found"));
            boolean hasInProgress = attemptRepository.findByQuizIdAndStudentIdAndStatus(
                    testId, student.getId(), QuizAttemptStatus.IN_PROGRESS).isPresent()
                    || attemptRepository.findAllByQuizIdAndStudentId(testId, student.getId()).stream()
                            .anyMatch(a -> a.getStatus() == QuizAttemptStatus.IN_PROGRESS);
            if (!hasInProgress) {
                throw new IllegalStateException("Active quiz attempt is required to view questions");
            }
        }
        return getQuestionsWithOptions(testId);
    }

    public List<Map<String, Object>> getQuestionsWithOptions(UUID testId) {
        List<QuizQuestion> questions = questionRepository.findAllByTestIdOrderByOrderingAsc(testId);
        List<UUID> qIds = questions.stream().map(QuizQuestion::getId).collect(Collectors.toList());
        List<QuizOption> allOptions = qIds.isEmpty() ? Collections.emptyList() : optionRepository.findAllByQuestionIdIn(qIds);
        
        return questions.stream().map(q -> {
            List<QuizOption> options = allOptions.stream()
                .filter(o -> o.getQuestionId().equals(q.getId()))
                .collect(Collectors.toList());
            Map<String, Object> map = new HashMap<>();
            map.put("question", q);
            map.put("options", options);
            return map;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void autoSubmitExpired() {
        List<QuizAttempt> activeAttempts = attemptRepository.findAllByStatus(QuizAttemptStatus.IN_PROGRESS);
        Instant now = Instant.now();

        for (QuizAttempt attempt : activeAttempts) {
            testRepository.findById(attempt.getQuizId()).ifPresent(test -> {
                int timeLimit = test.getTimeLimitMinutes() != null ? test.getTimeLimitMinutes() : 60;
                Instant deadline = attempt.getStartedAt().plus(timeLimit, ChronoUnit.MINUTES).plusSeconds(30);
                if (now.isAfter(deadline)) {
                    attempt.setScore(attempt.getScore() != null ? attempt.getScore() : 0);
                    attempt.setCompletedAt(now);
                    attempt.setStatus(QuizAttemptStatus.COMPLETED);
                    attemptRepository.save(attempt);
                    if (leaderboardService != null) {
                        leaderboardService.broadcastUpdate();
                    }
                    if (auditService != null) {
                        UUID userId = studentRepository.findById(attempt.getStudentId())
                                .map(Student::getUserId)
                                .orElse(null);
                        auditService.logEvent(
                                userId,
                                "SYSTEM",
                                "SYSTEM",
                                "QUIZ_AUTO_SUBMIT",
                                attempt.getQuizId().toString(),
                                "IN_PROGRESS",
                                "COMPLETED (score=" + attempt.getScore() + ")",
                                null
                        );
                    }
                }
            });
        }
    }

    private boolean isAdminOrInstructor(User user) {
        if (user != null) {
            return user.getRole() == Role.ADMIN || user.getRole() == Role.INSTRUCTOR;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        if (auth.getPrincipal() instanceof User u) {
            return u.getRole() == Role.ADMIN || u.getRole() == Role.INSTRUCTOR;
        }
        return auth.getAuthorities().stream().anyMatch(a -> {
            String r = a.getAuthority();
            return "ROLE_ADMIN".equals(r) || "ADMIN".equals(r)
                    || "ROLE_INSTRUCTOR".equals(r) || "INSTRUCTOR".equals(r)
                    || "ROLE_TEACHER".equals(r) || "TEACHER".equals(r);
        });
    }

    private UUID resolveUserId(User user) {
        if (user != null && user.getId() != null) {
            return user.getId();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            if (auth.getPrincipal() instanceof User u) {
                return u.getId();
            }
            if (userRepository != null && auth.getName() != null) {
                return userRepository.findByUsername(auth.getName())
                        .or(() -> userRepository.findByEmail(auth.getName()))
                        .map(User::getId)
                        .orElse(null);
            }
        }
        return null;
    }
}
