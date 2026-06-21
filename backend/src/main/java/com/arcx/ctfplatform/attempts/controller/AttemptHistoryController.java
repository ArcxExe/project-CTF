package com.arcx.ctfplatform.attempts.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.arcx.ctfplatform.attempts.dto.AttemptHistoryResponse;
import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.users.entity.User;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/attempts")
@RequiredArgsConstructor
public class AttemptHistoryController {

    private final StudentRepository studentRepository;
    private final AttemptRepository attemptRepository;

    @GetMapping("/history")
    public ResponseEntity<List<AttemptHistoryResponse>> getHistory(
            @RequestParam(required = false) UUID challengeId,
            @AuthenticationPrincipal User user) {
        
        Student student = studentRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        List<AttemptHistoryResponse> history;
        if (challengeId != null) {
            history = attemptRepository.findHistoryByStudentIdAndChallengeId(student.getId(), challengeId);
        } else {
            history = attemptRepository.findHistoryByStudentId(student.getId());
        }
        return ResponseEntity.ok(history);
    }
}
