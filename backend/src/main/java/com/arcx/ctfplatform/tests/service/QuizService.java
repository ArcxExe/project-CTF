package com.arcx.ctfplatform.tests.service;

import com.arcx.ctfplatform.tests.entity.QuizOption;
import com.arcx.ctfplatform.tests.entity.QuizQuestion;
import com.arcx.ctfplatform.tests.entity.QuizSubmission;
import com.arcx.ctfplatform.tests.entity.Test;
import com.arcx.ctfplatform.tests.repository.QuizOptionRepository;
import com.arcx.ctfplatform.tests.repository.QuizQuestionRepository;
import com.arcx.ctfplatform.tests.repository.QuizSubmissionRepository;
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

    private final QuizSubmissionRepository submissionRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizOptionRepository optionRepository;
    private final TestRepository testRepository;
    private final StudentRepository studentRepository;

    @Transactional
    public QuizSubmission startQuiz(UUID testId, UUID userId) {
        Student student = studentRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new IllegalArgumentException("Test not found"));

        Optional<QuizSubmission> activeSubmission = submissionRepository.findByTestIdAndStudentIdAndIsActiveTrue(testId, student.getId());
        if (activeSubmission.isPresent()) {
            return activeSubmission.get();
        }

        QuizSubmission submission = QuizSubmission.builder()
                .testId(testId)
                .studentId(student.getId())
                .startedAt(Instant.now())
                .isActive(true)
                .score(0)
                .build();

        return submissionRepository.save(submission);
    }

    @Transactional
    public QuizSubmission submitAnswers(UUID submissionId, Map<UUID, List<String>> answers) {
        QuizSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found"));

        if (!submission.isActive()) {
            throw new IllegalStateException("Submission is already completed");
        }

        Test test = testRepository.findById(submission.getTestId())
                .orElseThrow(() -> new IllegalArgumentException("Test not found"));

        Instant now = Instant.now();
        Instant deadline = submission.getStartedAt().plus(test.getTimeLimitMinutes(), ChronoUnit.MINUTES);
        
        // Even if past deadline, we grade whatever was submitted up to this point
        
        List<QuizQuestion> questions = questionRepository.findAllByTestIdOrderByOrderingAsc(test.getId());
        List<UUID> questionIds = questions.stream().map(QuizQuestion::getId).collect(Collectors.toList());
        List<QuizOption> options = optionRepository.findAllByQuestionIdIn(questionIds);

        int totalScore = 0;

        for (QuizQuestion q : questions) {
            List<String> studentAnswer = answers.getOrDefault(q.getId(), Collections.emptyList());
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

        submission.setScore(totalScore);
        submission.setSubmittedAt(now);
        submission.setActive(false);

        return submissionRepository.save(submission);
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
}
