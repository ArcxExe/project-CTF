package com.arcx.ctfplatform.tests.controller;

import com.arcx.ctfplatform.tests.dto.QuestionAnswerDTO;
import com.arcx.ctfplatform.tests.dto.OptionForStudentDTO;
import com.arcx.ctfplatform.tests.dto.QuestionForStudentDTO;
import com.arcx.ctfplatform.tests.entity.QuizAttempt;
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

    @PostMapping("/{quizId}/start")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<QuizAttempt> startQuiz(
            @PathVariable UUID quizId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(quizService.startQuiz(quizId, user.getId()));
    }

    @GetMapping("/attempts")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<QuizAttempt>> getStudentAttempts(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(quizService.getStudentAttempts(user.getId()));
    }

    @PostMapping("/{quizId}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<QuizAttempt> submitAnswers(
            @PathVariable UUID quizId,
            @AuthenticationPrincipal User user,
            @RequestBody(required = false) List<QuestionAnswerDTO> answers) {
        return ResponseEntity.ok(quizService.submitAnswers(quizId, user.getId(), answers));
    }

    @GetMapping("/{quizId}/questions")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<QuestionForStudentDTO>> getTestQuestions(
            @PathVariable UUID quizId,
            @AuthenticationPrincipal User user) {
        List<Map<String, Object>> data = quizService.getQuestionsForCaller(quizId, user);
        
        List<QuestionForStudentDTO> response = data.stream().map(map -> {
            var q = (com.arcx.ctfplatform.tests.entity.QuizQuestion) map.get("question");
            @SuppressWarnings("unchecked")
            var opts = (List<com.arcx.ctfplatform.tests.entity.QuizOption>) map.get("options");
            
            List<OptionForStudentDTO> studentOpts = opts.stream().map(o ->
                new OptionForStudentDTO(o.getId(), o.getQuestionId(), o.getOptionText(), null)
            ).toList();
            
            return new QuestionForStudentDTO(
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

    public ResponseEntity<List<QuestionForStudentDTO>> getTestQuestions(UUID quizId) {
        return getTestQuestions(quizId, null);
    }
}
