package com.arcx.ctfplatform.tests.controller;

import com.arcx.ctfplatform.tests.entity.QuizSubmission;
import com.arcx.ctfplatform.tests.service.QuizService;
import com.arcx.ctfplatform.users.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @PostMapping("/{testId}/start")
    public ResponseEntity<QuizSubmission> startQuiz(
            @PathVariable UUID testId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(quizService.startQuiz(testId, user.getId()));
    }

    @PostMapping("/submissions/{submissionId}/submit")
    public ResponseEntity<QuizSubmission> submitAnswers(
            @PathVariable UUID submissionId,
            @RequestBody Map<UUID, List<String>> answers) {
        return ResponseEntity.ok(quizService.submitAnswers(submissionId, answers));
    }

    @PutMapping("/questions/{questionId}")
    public ResponseEntity<com.arcx.ctfplatform.tests.entity.QuizQuestion> updateQuestion(
            @PathVariable UUID questionId,
            @RequestBody com.arcx.ctfplatform.tests.entity.QuizQuestion questionUpdates) {
        return ResponseEntity.ok(quizService.updateQuestion(questionId, questionUpdates));
    }

    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<Void> deleteQuestion(@PathVariable UUID questionId) {
        quizService.deleteQuestion(questionId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/options/{optionId}")
    public ResponseEntity<com.arcx.ctfplatform.tests.entity.QuizOption> updateOption(
            @PathVariable UUID optionId,
            @RequestBody com.arcx.ctfplatform.tests.entity.QuizOption optionUpdates) {
        return ResponseEntity.ok(quizService.updateOption(optionId, optionUpdates));
    }

    @DeleteMapping("/options/{optionId}")
    public ResponseEntity<Void> deleteOption(@PathVariable UUID optionId) {
        quizService.deleteOption(optionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{testId}/questions")
    public ResponseEntity<List<Map<String, Object>>> getTestQuestions(@PathVariable UUID testId) {
        return ResponseEntity.ok(quizService.getQuestionsWithOptions(testId));
    }
}
