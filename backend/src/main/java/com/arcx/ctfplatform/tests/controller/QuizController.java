package com.arcx.ctfplatform.tests.controller;

import com.arcx.ctfplatform.tests.dto.QuizOptionStudentResponse;
import com.arcx.ctfplatform.tests.dto.QuizQuestionStudentResponse;
import com.arcx.ctfplatform.tests.entity.QuizSubmission;
import com.arcx.ctfplatform.tests.service.QuizService;
import com.arcx.ctfplatform.users.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.arcx.ctfplatform.tests.entity.QuizQuestion> updateQuestion(
            @PathVariable UUID questionId,
            @RequestBody com.arcx.ctfplatform.tests.entity.QuizQuestion questionUpdates) {
        return ResponseEntity.ok(quizService.updateQuestion(questionId, questionUpdates));
    }

    @DeleteMapping("/questions/{questionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteQuestion(@PathVariable UUID questionId) {
        quizService.deleteQuestion(questionId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/options/{optionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.arcx.ctfplatform.tests.entity.QuizOption> updateOption(
            @PathVariable UUID optionId,
            @RequestBody com.arcx.ctfplatform.tests.entity.QuizOption optionUpdates) {
        return ResponseEntity.ok(quizService.updateOption(optionId, optionUpdates));
    }

    @DeleteMapping("/options/{optionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteOption(@PathVariable UUID optionId) {
        quizService.deleteOption(optionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{testId}/questions")
    public ResponseEntity<List<QuizQuestionStudentResponse>> getTestQuestions(@PathVariable UUID testId) {
        List<Map<String, Object>> data = quizService.getQuestionsWithOptions(testId);
        
        List<QuizQuestionStudentResponse> response = data.stream().map(map -> {
            var q = (com.arcx.ctfplatform.tests.entity.QuizQuestion) map.get("question");
            @SuppressWarnings("unchecked")
            var opts = (List<com.arcx.ctfplatform.tests.entity.QuizOption>) map.get("options");
            
            List<QuizOptionStudentResponse> studentOpts = opts.stream().map(o -> 
                new QuizOptionStudentResponse(o.getId(), o.getQuestionId(), o.getOptionText(), o.getSequenceOrder())
            ).toList();
            
            return new QuizQuestionStudentResponse(
                q.getId(),
                q.getTestId(),
                q.getType(),
                q.getText(),
                q.getPoints(),
                q.getOrdering(),
                studentOpts
            );
        }).toList();
        
        return ResponseEntity.ok(response);
    }
}
