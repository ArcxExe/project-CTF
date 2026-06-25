package com.arcx.ctfplatform.modifiers.controller;

import com.arcx.ctfplatform.common.config.IMapping;
import com.arcx.ctfplatform.modifiers.dto.ScoreAdjustmentResponse;
import com.arcx.ctfplatform.modifiers.entity.ScoreAdjustment;
import com.arcx.ctfplatform.modifiers.service.ScoreAdjustmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/score-adjustments")
@RequiredArgsConstructor
public class ScoreAdjustmentController {

    private final ScoreAdjustmentService scoreAdjustmentService;
    private final IMapping<ScoreAdjustment, ScoreAdjustmentResponse> scoreAdjustmentMapper;

    @GetMapping
    public ResponseEntity<List<ScoreAdjustmentResponse>> getAllAdjustments() {
        List<ScoreAdjustment> adjustments = scoreAdjustmentService.getAllAdjustments();
        List<ScoreAdjustmentResponse> response = adjustments.stream()
                .map(scoreAdjustmentMapper::mapping)
                .toList();
        return ResponseEntity.ok(response);
    }
}
