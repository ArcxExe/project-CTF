package com.arcx.ctfplatform.attempts.controller;

import com.arcx.ctfplatform.attempts.dto.AttemptResponse;
import com.arcx.ctfplatform.attempts.entity.Attempt;
import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.attempts.service.AttemptService;
import com.arcx.ctfplatform.common.config.IMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/attempts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAttemptController {

    private final AttemptService attemptService;
    private final AttemptRepository attemptRepository;
    private final IMapping<Attempt, AttemptResponse> attemptMapper;

    public record GradeRequest(int score, double manualPercentMultiplier) {}

    @PostMapping("/{id}/grade")
    public ResponseEntity<Attempt> gradeAttempt(
            @PathVariable UUID id,
            @RequestBody GradeRequest request) {
        return ResponseEntity.ok(attemptService.gradeAttempt(id, request.score(), request.manualPercentMultiplier()));
    }

    @GetMapping("/pending-review")
    public ResponseEntity<List<AttemptResponse>> getPendingReviewAttempts() {
        List<Attempt> pendingAttempts = attemptRepository.findAllByFilePathIsNotNullAndIsCorrectFalse();
        return ResponseEntity.ok(attemptMapper.mappingList(pendingAttempts));
    }
}
