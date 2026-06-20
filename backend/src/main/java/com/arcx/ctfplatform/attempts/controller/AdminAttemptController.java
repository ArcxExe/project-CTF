package com.arcx.ctfplatform.attempts.controller;

import com.arcx.ctfplatform.attempts.entity.Attempt;
import com.arcx.ctfplatform.attempts.service.AttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/attempts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAttemptController {

    private final AttemptService attemptService;

    public record GradeRequest(int score, double manualPercentMultiplier) {}

    @PostMapping("/{id}/grade")
    public ResponseEntity<Attempt> gradeAttempt(
            @PathVariable UUID id,
            @RequestBody GradeRequest request) {
        return ResponseEntity.ok(attemptService.gradeAttempt(id, request.score(), request.manualPercentMultiplier()));
    }
}
