package com.arcx.ctfplatform.tests.service;

import com.arcx.ctfplatform.tests.dto.QuestionAnswerDTO;
import com.arcx.ctfplatform.tests.entity.QuizAttempt;
import com.arcx.ctfplatform.tests.entity.QuizAttemptStatus;
import com.arcx.ctfplatform.tests.entity.QuizOption;
import com.arcx.ctfplatform.tests.entity.QuizQuestion;
import com.arcx.ctfplatform.tests.entity.Test;
import com.arcx.ctfplatform.tests.repository.QuizAttemptRepository;
import com.arcx.ctfplatform.tests.repository.QuizOptionRepository;
import com.arcx.ctfplatform.tests.repository.QuizQuestionRepository;
import com.arcx.ctfplatform.tests.repository.TestRepository;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuizService {

    private final QuizAttemptRepository attemptRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizOptionRepository optionRepository;
    private final TestRepository testRepository;
    private final StudentRepository studentRepository;

    @Transactional
    public QuizAttempt startQuiz(UUID quizId, UUID userId) {
        Student student = studentRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        
        Test test = testRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));

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
        
        List<QuizQuestion> questions = questionRepository.findAllByTestIdOrderByOrderingAsc(test.getId());
        List<UUID> questionIds = questions.stream().map(QuizQuestion::getId).collect(Collectors.toList());
        List<QuizOption> options = optionRepository.findAllByQuestionIdIn(questionIds);

        Map<UUID, List<String>> answerMap = answers.stream()
                .collect(Collectors.toMap(QuestionAnswerDTO::questionId, QuestionAnswerDTO::answers));

        int totalScore = 0;

        for (QuizQuestion q : questions) {
            List<String> studentAnswer = answerMap.getOrDefault(q.getId(), Collections.emptyList());
            List<QuizOption> qOptions = options.stream()
                    .filter(o -> o.getQuestionId().equals(q.getId()))
                    .collect(Collectors.toList());

            if ("SEQUENCE".equalsIgnoreCase(q.getType())) {
                List<String> correctSequence = qOptions.stream()
                        .sorted(Comparator.comparing(QuizOption::getSequenceOrder))
                        .map(o -> o.getId().toString())
                        .collect(Collectors.toList());
                if (correctSequence.equals(studentAnswer)) {
                    totalScore += q.getPoints();
                }
            } else {
                List<String> correctOptionIds = qOptions.stream()
                        .filter(QuizOption::isCorrect)
                        .map(o -> o.getId().toString())
                        .collect(Collectors.toList());
                
                // For choice/multiple choice, exact match required
                if (new HashSet<>(correctOptionIds).equals(new HashSet<>(studentAnswer))) {
                    totalScore += q.getPoints();
                }
            }
        }

        attempt.setScore(totalScore);
        attempt.setCompletedAt(now);
        attempt.setStatus(QuizAttemptStatus.COMPLETED);

        return attemptRepository.save(attempt);
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
                Instant deadline = attempt.getStartedAt().plus(test.getTimeLimitMinutes(), ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES);
                if (now.isAfter(deadline)) {
                    studentRepository.findById(attempt.getStudentId()).ifPresent(student -> {
                        if (student.getUserId() != null) {
                            try {
                                submitAnswers(attempt.getQuizId(), student.getUserId(), Collections.emptyList());
                            } catch (Exception e) {
                                // In case of error (like already submitted concurrently), ignore
                            }
                        }
                    });
                }
            });
        }
    }
}
