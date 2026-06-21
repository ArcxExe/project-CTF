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
    public ResponseEntity<QuizOption> updateOption(@PathVariable UUID optionId, @RequestBody QuizOption optionDetails) {
        QuizOption option = optionRepository.findById(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Option not found"));
        option.setOptionText(optionDetails.getOptionText());
        option.setCorrect(optionDetails.isCorrect());
        option.setSequenceOrder(optionDetails.getSequenceOrder());
        if (optionDetails.getQuestionId() != null) {
            option.setQuestionId(optionDetails.getQuestionId());
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
