package com.arcx.ctfplatform.tests.service;

import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.entity.StudentStatus;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.audit.service.AuditService;
import com.arcx.ctfplatform.leaderboard.service.LeaderboardService;
import com.arcx.ctfplatform.tests.dto.QuestionAnswerDTO;
import com.arcx.ctfplatform.tests.dto.QuizOptionRequest;
import com.arcx.ctfplatform.tests.dto.QuizOptionUpdateRequest;
import com.arcx.ctfplatform.tests.dto.QuizQuestionRequest;
import com.arcx.ctfplatform.tests.dto.QuizQuestionUpdateRequest;
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

    private static final Set<String> ALLOWED_QUESTION_TYPES = Set.of("RADIO", "CHECKBOX", "SEQUENCE");

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

    static final long RESUME_EXPIRY_GRACE_SECONDS = 30L;
    static final long SUBMIT_GRACE_SECONDS = 90L;
    static final long SCHEDULER_EXPIRY_GRACE_SECONDS = 120L;

    @Transactional
    public QuizAttempt startQuiz(UUID quizId, UUID userId) {
        Student student = studentRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("Профиль студента не найден"));
        if (student.getStatus() == StudentStatus.PENDING_BINDING_VERIFICATION) {
            throw new IllegalStateException("Прохождение теста недоступно: ваша учётная запись ожидает подтверждения преподавателем");
        }
        if (student.getStatus() == StudentStatus.BLOCKED || student.getStatus() == StudentStatus.DISQUALIFIED) {
            throw new IllegalStateException("Прохождение теста недоступно: учётная запись заблокирована или дисквалифицирована");
        }
        
        Test test = testRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Тест не найден"));

        if (test.getStatus() != TestStatus.PUBLISHED) {
            throw new IllegalStateException("Тест ещё не опубликован");
        }

        List<QuizAttempt> attempts = attemptRepository.findAllByQuizIdAndStudentId(quizId, student.getId());
        Instant now = Instant.now();

        boolean hasCompleted = attempts.stream()
                .anyMatch(a -> a.getStatus() == QuizAttemptStatus.COMPLETED);
        if (hasCompleted) {
            throw new IllegalStateException("Вы уже прошли этот тест");
        }

        List<QuizAttempt> activeAttempts = attempts.stream()
                .filter(a -> a.getStatus() == QuizAttemptStatus.IN_PROGRESS)
                .sorted(Comparator.comparing(QuizAttempt::getStartedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (!activeAttempts.isEmpty()) {
            QuizAttempt existing = activeAttempts.get(0);
            if (activeAttempts.size() > 1) {
                attemptRepository.deleteAll(activeAttempts.subList(1, activeAttempts.size()));
            }
            if (isAttemptExpired(test, existing, now, RESUME_EXPIRY_GRACE_SECONDS)) {
                return enrichTimingMetadata(completeExpiredAttempt(existing, now, userId), test, now);
            }
            return enrichTimingMetadata(existing, test, now);
        }

        QuizAttempt attempt = QuizAttempt.builder()
                .quizId(quizId)
                .studentId(student.getId())
                .startedAt(now)
                .status(QuizAttemptStatus.IN_PROGRESS)
                .score(0)
                .build();

        return enrichTimingMetadata(attemptRepository.save(attempt), test, now);
    }

    @Transactional(readOnly = true)
    public List<QuizAttempt> getStudentAttempts(UUID userId) {
        Student student = studentRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Профиль студента не найден"));
        List<QuizAttempt> attempts = attemptRepository.findAllByStudentId(student.getId());
        if (attempts.isEmpty()) {
            return attempts;
        }
        Instant now = Instant.now();
        Set<UUID> quizIds = attempts.stream()
                .map(QuizAttempt::getQuizId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Test> testsById = testRepository.findAllById(quizIds).stream()
                .collect(Collectors.toMap(Test::getId, t -> t, (a, b) -> a));
        attempts.forEach(attempt -> enrichTimingMetadata(attempt, testsById.get(attempt.getQuizId()), now));
        return attempts;
    }

    @Transactional
    public QuizAttempt submitAnswers(UUID quizId, UUID userId, List<QuestionAnswerDTO> answers) {
        Student student = studentRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("Профиль студента не найден"));

        List<QuizAttempt> activeAttempts = attemptRepository.findAllByQuizIdAndStudentId(quizId, student.getId()).stream()
                .filter(a -> a.getStatus() == QuizAttemptStatus.IN_PROGRESS)
                .sorted(Comparator.comparing(QuizAttempt::getStartedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (activeAttempts.isEmpty()) {
            throw new IllegalStateException("Активная попытка прохождения теста не найдена");
        }
        QuizAttempt attempt = activeAttempts.get(0);

        Test test = testRepository.findById(attempt.getQuizId())
                .orElseThrow(() -> new IllegalArgumentException("Тест не найден"));

        Instant now = Instant.now();
        if (isAttemptExpired(test, attempt, now, SUBMIT_GRACE_SECONDS)) {
            if (activeAttempts.size() > 1) {
                attemptRepository.deleteAll(activeAttempts.subList(1, activeAttempts.size()));
            }
            return enrichTimingMetadata(completeExpiredAttempt(attempt, now, userId), test, now);
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

        if (activeAttempts.size() > 1) {
            attemptRepository.deleteAll(activeAttempts.subList(1, activeAttempts.size()));
        }
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
        return enrichTimingMetadata(saved, test, now);
    }

    @Transactional
    public QuizQuestion addQuestion(UUID testId, QuizQuestionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Question payload is required");
        }
        testRepository.findById(testId)
                .orElseThrow(() -> new IllegalArgumentException("Тест не найден"));

        String normalizedType = validateAndNormalizeQuestionType(request.type());
        if (request.text() == null || request.text().isBlank()) {
            throw new IllegalArgumentException("Question text is required");
        }
        if (request.points() == null || request.points() < 0) {
            throw new IllegalArgumentException("Points must be non-negative");
        }
        if (request.ordering() == null || request.ordering() < 0) {
            throw new IllegalArgumentException("Ordering must be non-negative");
        }

        QuizQuestion question = QuizQuestion.builder()
                .testId(testId)
                .type(normalizedType)
                .text(request.text())
                .points(request.points())
                .ordering(request.ordering())
                .build();

        QuizQuestion saved = questionRepository.save(question);
        syncTestQuestionsCount(testId);
        return saved;
    }

    @Transactional
    public QuizQuestion updateQuestion(UUID questionId, QuizQuestionUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Question payload is required");
        }
        QuizQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found"));

        if (request.type() != null) {
            String normalizedType = validateAndNormalizeQuestionType(request.type());
            question.setType(normalizedType);
        }
        if (request.text() != null) {
            if (request.text().isBlank()) {
                throw new IllegalArgumentException("Question text is required");
            }
            question.setText(request.text());
        }
        if (request.points() != null) {
            if (request.points() < 0) {
                throw new IllegalArgumentException("Points must be non-negative");
            }
            question.setPoints(request.points());
        }
        if (request.ordering() != null) {
            if (request.ordering() < 0) {
                throw new IllegalArgumentException("Ordering must be non-negative");
            }
            question.setOrdering(request.ordering());
        }
        if (request.testId() != null) {
            question.setTestId(request.testId());
        }

        if ("RADIO".equalsIgnoreCase(question.getType())) {
            List<QuizOption> options = optionRepository.findAllByQuestionIdOrderBySequenceOrderAsc(question.getId());
            boolean foundFirstCorrect = false;
            for (QuizOption opt : options) {
                if (opt.isCorrect()) {
                    if (!foundFirstCorrect) {
                        foundFirstCorrect = true;
                    } else {
                        opt.setCorrect(false);
                        optionRepository.save(opt);
                    }
                }
            }
        }

        return questionRepository.save(question);
    }

    @Transactional
    public QuizQuestion updateQuestion(UUID questionId, QuizQuestion questionUpdates) {
        return updateQuestion(
                questionId,
                new QuizQuestionUpdateRequest(
                        questionUpdates.getType(),
                        questionUpdates.getText(),
                        questionUpdates.getPoints(),
                        questionUpdates.getOrdering(),
                        questionUpdates.getTestId()
                )
        );
    }

    @Transactional
    public void deleteQuestion(UUID questionId) {
        QuizQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found"));
        UUID testId = question.getTestId();
        questionRepository.deleteById(questionId);
        if (testId != null) {
            syncTestQuestionsCount(testId);
        }
    }

    @Transactional
    public QuizOption addOption(UUID questionId, QuizOptionRequest request) {
        if (request == null || request.optionText() == null || request.optionText().isBlank()) {
            throw new IllegalArgumentException("Option text is required");
        }
        QuizQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found"));

        boolean isCorrect = request.resolvedCorrect();
        if (isCorrect) {
            clearOtherCorrectOptionsIfRadio(question, null);
        }

        QuizOption option = QuizOption.builder()
                .questionId(questionId)
                .optionText(request.optionText())
                .isCorrect(isCorrect)
                .sequenceOrder(request.sequenceOrder())
                .build();

        return optionRepository.save(option);
    }

    @Transactional
    public QuizOption updateOption(UUID optionId, QuizOptionUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Option payload is required");
        }
        QuizOption option = optionRepository.findById(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Option not found"));

        if (request.optionText() != null) {
            if (request.optionText().isBlank()) {
                throw new IllegalArgumentException("Option text is required");
            }
            option.setOptionText(request.optionText());
        }
        if (request.questionId() != null) {
            option.setQuestionId(request.questionId());
        }
        if (request.sequenceOrder() != null) {
            option.setSequenceOrder(request.sequenceOrder());
        }
        if (request.hasCorrectFlag()) {
            boolean newCorrect = request.resolvedCorrect();
            option.setCorrect(newCorrect);
            if (newCorrect) {
                QuizQuestion question = questionRepository.findById(option.getQuestionId())
                        .orElseThrow(() -> new IllegalArgumentException("Question not found"));
                clearOtherCorrectOptionsIfRadio(question, option.getId());
            }
        }

        return optionRepository.save(option);
    }

    @Transactional
    public QuizOption updateOption(UUID optionId, QuizOption optionUpdates) {
        return updateOption(
                optionId,
                new QuizOptionUpdateRequest(
                        optionUpdates.getOptionText(),
                        optionUpdates.isCorrect(),
                        null,
                        optionUpdates.getSequenceOrder(),
                        optionUpdates.getQuestionId()
                )
        );
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
                .orElseThrow(() -> new IllegalArgumentException("Тест не найден"));

        if (!isAdminOrInstructor(user)) {
            if (test.getStatus() != TestStatus.PUBLISHED) {
                throw new IllegalStateException("Тест ещё не опубликован");
            }
            UUID resolvedUserId = resolveUserId(user);
            if (resolvedUserId == null) {
                throw new IllegalStateException("Для просмотра вопросов необходимо начать попытку прохождения теста");
            }
            Student student = studentRepository.findByUserId(resolvedUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Профиль студента не найден"));
            QuizAttempt activeAttempt = attemptRepository.findAllByQuizIdAndStudentId(testId, student.getId()).stream()
                    .filter(a -> a.getStatus() == QuizAttemptStatus.IN_PROGRESS)
                    .min(Comparator.comparing(QuizAttempt::getStartedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElseThrow(() -> new IllegalStateException("Для просмотра вопросов необходимо начать попытку прохождения теста"));
            UUID shuffleSeedId = activeAttempt.getId() != null ? activeAttempt.getId() : student.getId();
            return getQuestionsWithOptions(testId, shuffleSeedId);
        }
        return getQuestionsWithOptions(testId);
    }

    public List<Map<String, Object>> getQuestionsWithOptions(UUID testId) {
        return getQuestionsWithOptions(testId, null);
    }

    private List<Map<String, Object>> getQuestionsWithOptions(UUID testId, UUID shuffleSeedId) {
        List<QuizQuestion> questions = questionRepository.findAllByTestIdOrderByOrderingAsc(testId);
        List<UUID> qIds = questions.stream().map(QuizQuestion::getId).collect(Collectors.toList());
        List<QuizOption> allOptions = qIds.isEmpty() ? Collections.emptyList() : optionRepository.findAllByQuestionIdIn(qIds);

        return questions.stream().map(q -> {
            List<QuizOption> options = allOptions.stream()
                    .filter(o -> o.getQuestionId().equals(q.getId()))
                    .collect(Collectors.toCollection(ArrayList::new));

            if ("SEQUENCE".equalsIgnoreCase(q.getType())) {
                options.sort(Comparator
                        .comparing(QuizOption::getSequenceOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(QuizOption::getId, Comparator.nullsLast(Comparator.naturalOrder())));

                if (shuffleSeedId != null && options.size() > 1) {
                    List<QuizOption> canonicalOrder = List.copyOf(options);
                    long seed = shuffleSeedId.getMostSignificantBits()
                            ^ Long.rotateLeft(shuffleSeedId.getLeastSignificantBits(), 13)
                            ^ Long.rotateLeft(q.getId().getMostSignificantBits(), 29)
                            ^ Long.rotateLeft(q.getId().getLeastSignificantBits(), 47);
                    Collections.shuffle(options, new Random(seed));
                    if (options.equals(canonicalOrder)) {
                        Collections.rotate(options, 1);
                    }
                }
            }

            Map<String, Object> map = new HashMap<>();
            map.put("question", q);
            map.put("options", options);
            return map;
        }).collect(Collectors.toList());
    }

    private String validateAndNormalizeQuestionType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Question type is required");
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_QUESTION_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported question type: " + type);
        }
        return normalized;
    }

    private void clearOtherCorrectOptionsIfRadio(QuizQuestion question, UUID keepOptionId) {
        if (!"RADIO".equalsIgnoreCase(question.getType())) {
            return;
        }
        List<QuizOption> existingOptions = optionRepository.findAllByQuestionIdOrderBySequenceOrderAsc(question.getId());
        for (QuizOption other : existingOptions) {
            if ((keepOptionId == null || !other.getId().equals(keepOptionId)) && other.isCorrect()) {
                other.setCorrect(false);
                optionRepository.save(other);
            }
        }
    }

    private void syncTestQuestionsCount(UUID testId) {
        testRepository.findById(testId).ifPresent(test -> {
            int count = questionRepository.findAllByTestIdOrderByOrderingAsc(testId).size();
            test.setQuestionsCount(count);
            testRepository.save(test);
        });
    }

    @Transactional
    public void autoSubmitExpired() {
        List<QuizAttempt> activeAttempts = attemptRepository.findAllByStatus(QuizAttemptStatus.IN_PROGRESS);
        Instant now = Instant.now();

        for (QuizAttempt attempt : activeAttempts) {
            testRepository.findById(attempt.getQuizId()).ifPresent(test -> {
                if (isAttemptExpired(test, attempt, now, SCHEDULER_EXPIRY_GRACE_SECONDS)) {
                    UUID userId = studentRepository.findById(attempt.getStudentId())
                            .map(Student::getUserId)
                            .orElse(null);
                    completeExpiredAttempt(attempt, now, userId);
                }
            });
        }
    }

    private boolean isAttemptExpired(Test test, QuizAttempt attempt, Instant now, long graceSeconds) {
        if (attempt.getStartedAt() == null) {
            return false;
        }
        Integer configuredLimit = test.getTimeLimitMinutes();
        if (configuredLimit == null || configuredLimit <= 0) {
            return false;
        }
        Instant deadline = attempt.getStartedAt()
                .plus(configuredLimit, ChronoUnit.MINUTES)
                .plusSeconds(Math.max(0L, graceSeconds));
        return now.isAfter(deadline);
    }

    private QuizAttempt enrichTimingMetadata(QuizAttempt attempt, Test test, Instant now) {
        if (attempt == null) {
            return null;
        }
        attempt.setServerNow(now);
        if (attempt.getStatus() == QuizAttemptStatus.COMPLETED) {
            attempt.setRemainingSeconds(0L);
            return attempt;
        }
        if (test != null
                && test.getTimeLimitMinutes() != null
                && test.getTimeLimitMinutes() > 0
                && attempt.getStartedAt() != null) {
            Instant deadline = attempt.getStartedAt().plus(test.getTimeLimitMinutes(), ChronoUnit.MINUTES);
            long remaining = Math.max(0L, ChronoUnit.SECONDS.between(now, deadline));
            attempt.setRemainingSeconds(remaining);
        } else {
            attempt.setRemainingSeconds(null);
        }
        return attempt;
    }

    private QuizAttempt completeExpiredAttempt(QuizAttempt attempt, Instant now, UUID userId) {
        attempt.setScore(attempt.getScore() != null ? attempt.getScore() : 0);
        attempt.setCompletedAt(now);
        attempt.setStatus(QuizAttemptStatus.COMPLETED);
        QuizAttempt saved = attemptRepository.save(attempt);
        if (leaderboardService != null) {
            leaderboardService.broadcastUpdate();
        }
        if (auditService != null) {
            auditService.logEvent(
                    userId,
                    "SYSTEM",
                    "SYSTEM",
                    "QUIZ_AUTO_SUBMIT",
                    saved.getQuizId().toString(),
                    "IN_PROGRESS",
                    "COMPLETED (score=" + saved.getScore() + ")",
                    null
            );
        }
        return saved;
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
