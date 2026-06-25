package com.arcx.ctfplatform.tests.controller;

import com.arcx.ctfplatform.tests.entity.QuizOption;
import com.arcx.ctfplatform.tests.entity.QuizQuestion;
import com.arcx.ctfplatform.tests.repository.QuizOptionRepository;
import com.arcx.ctfplatform.tests.repository.QuizQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.arcx.ctfplatform.tests.service.QuizService;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/quizzes")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminQuizController {

    private final QuizQuestionRepository questionRepository;
    private final QuizOptionRepository optionRepository;
    private final QuizService quizService;

    @PostMapping("/tests/{testId}/questions")
    public ResponseEntity<QuizQuestion> addQuestion(@PathVariable UUID testId, @RequestBody QuizQuestion question) {
        question.setTestId(testId);
        return ResponseEntity.ok(questionRepository.save(question));
    }

    @PostMapping("/questions/{questionId}/options")
    public ResponseEntity<QuizOption> addOption(@PathVariable UUID questionId, @RequestBody QuizOption option) {
        option.setQuestionId(questionId);
        return ResponseEntity.ok(optionRepository.save(option));
    }

    @PutMapping("/questions/{questionId}")
    public ResponseEntity<QuizQuestion> updateQuestion(@PathVariable UUID questionId, @RequestBody QuizQuestion questionDetails) {
        QuizQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found"));
        question.setType(questionDetails.getType());
        question.setText(questionDetails.getText());
        question.setPoints(questionDetails.getPoints());
        question.setOrdering(questionDetails.getOrdering());
        if (questionDetails.getTestId() != null) {
            question.setTestId(questionDetails.getTestId());
        }
        return ResponseEntity.ok(questionRepository.save(question));
    }

    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<Void> deleteQuestion(@PathVariable UUID questionId) {
        questionRepository.deleteById(questionId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/options/{optionId}")
    public ResponseEntity<QuizOption> updateOption(@PathVariable UUID optionId, @RequestBody Map<String, Object> payload) {
        QuizOption option = optionRepository.findById(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Option not found"));
        
        if (payload.containsKey("optionText")) {
            option.setOptionText((String) payload.get("optionText"));
        }
        if (payload.containsKey("correct")) {
            boolean newCorrect = (Boolean) payload.get("correct");
            option.setCorrect(newCorrect);
            
            if (newCorrect) {
                // If this is a RADIO question, set all other options of this question to correct = false
                QuizQuestion question = questionRepository.findById(option.getQuestionId())
                        .orElseThrow(() -> new IllegalArgumentException("Question not found"));
                if ("RADIO".equalsIgnoreCase(question.getType())) {
                    List<QuizOption> otherOptions = optionRepository.findAllByQuestionIdOrderBySequenceOrderAsc(option.getQuestionId());
                    for (QuizOption other : otherOptions) {
                        if (!other.getId().equals(option.getId()) && other.isCorrect()) {
                            other.setCorrect(false);
                            optionRepository.save(other);
                        }
                    }
                }
            }
        }
        if (payload.containsKey("sequenceOrder")) {
            Object seq = payload.get("sequenceOrder");
            option.setSequenceOrder(seq != null ? ((Number) seq).intValue() : null);
        }
        if (payload.containsKey("questionId")) {
            Object qId = payload.get("questionId");
            option.setQuestionId(qId != null ? UUID.fromString(qId.toString()) : null);
        }
        
        return ResponseEntity.ok(optionRepository.save(option));
    }

    @DeleteMapping("/options/{optionId}")
    public ResponseEntity<Void> deleteOption(@PathVariable UUID optionId) {
        optionRepository.deleteById(optionId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/tests/{testId}/questions")
    public ResponseEntity<List<Map<String, Object>>> getTestQuestionsAdmin(@PathVariable UUID testId) {
        return ResponseEntity.ok(quizService.getQuestionsWithOptions(testId));
    }
}
