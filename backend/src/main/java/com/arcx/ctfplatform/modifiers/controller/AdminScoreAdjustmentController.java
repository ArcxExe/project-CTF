package com.arcx.ctfplatform.modifiers.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arcx.ctfplatform.common.config.IMapping;
import com.arcx.ctfplatform.modifiers.dto.ScoreAdjustmentRequest;
import com.arcx.ctfplatform.modifiers.dto.ScoreAdjustmentResponse;
import com.arcx.ctfplatform.modifiers.entity.ScoreAdjustment;
import com.arcx.ctfplatform.modifiers.service.ScoreAdjustmentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/score-adjustments")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminScoreAdjustmentController {

    private final ScoreAdjustmentService scoreAdjustmentService;
    private final IMapping<ScoreAdjustment, ScoreAdjustmentResponse> scoreAdjustmentMapper;

    @PostMapping
    public ResponseEntity<ScoreAdjustmentResponse> createAdjustment(
            @Valid @RequestBody ScoreAdjustmentRequest request) {
        ScoreAdjustment adjustment = scoreAdjustmentService.createAdjustment(
                request.studentId(),
                request.competitionId(),
                request.points(),
                request.reason()
        );
        return ResponseEntity.ok(scoreAdjustmentMapper.mapping(adjustment));
    }
}
