package com.arcx.ctfplatform.tests.controller;

import com.arcx.ctfplatform.tests.dto.QuizOptionRequest;
import com.arcx.ctfplatform.tests.dto.QuizOptionUpdateRequest;
import com.arcx.ctfplatform.tests.dto.QuizQuestionRequest;
import com.arcx.ctfplatform.tests.dto.QuizQuestionUpdateRequest;
import com.arcx.ctfplatform.tests.entity.QuizOption;
import com.arcx.ctfplatform.tests.entity.QuizQuestion;
import com.arcx.ctfplatform.tests.service.QuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/quizzes")
@PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
@RequiredArgsConstructor
public class AdminQuizController {

    private final QuizService quizService;

    @PostMapping("/tests/{testId}/questions")
    public ResponseEntity<QuizQuestion> addQuestion(
            @PathVariable UUID testId,
            @Valid @RequestBody QuizQuestionRequest request) {
        return ResponseEntity.ok(quizService.addQuestion(testId, request));
    }

    @PostMapping("/questions/{questionId}/options")
    public ResponseEntity<QuizOption> addOption(
            @PathVariable UUID questionId,
            @Valid @RequestBody QuizOptionRequest request) {
        return ResponseEntity.ok(quizService.addOption(questionId, request));
    }

    @PutMapping("/questions/{questionId}")
    public ResponseEntity<QuizQuestion> updateQuestion(
            @PathVariable UUID questionId,
            @Valid @RequestBody QuizQuestionUpdateRequest request) {
        return ResponseEntity.ok(quizService.updateQuestion(questionId, request));
    }

    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<Void> deleteQuestion(@PathVariable UUID questionId) {
        quizService.deleteQuestion(questionId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/options/{optionId}")
    public ResponseEntity<QuizOption> updateOption(
            @PathVariable UUID optionId,
            @Valid @RequestBody QuizOptionUpdateRequest request) {
        return ResponseEntity.ok(quizService.updateOption(optionId, request));
    }

    @DeleteMapping("/options/{optionId}")
    public ResponseEntity<Void> deleteOption(@PathVariable UUID optionId) {
        quizService.deleteOption(optionId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/tests/{testId}/questions")
    public ResponseEntity<List<Map<String, Object>>> getTestQuestionsAdmin(@PathVariable UUID testId) {
        return ResponseEntity.ok(quizService.getQuestionsWithOptions(testId));
    }
}
