package com.arcx.ctfplatform.modifiers.controller;

import com.arcx.ctfplatform.common.config.IMapping;
import com.arcx.ctfplatform.modifiers.dto.ScoreAdjustmentResponse;
import com.arcx.ctfplatform.modifiers.entity.ScoreAdjustment;
import com.arcx.ctfplatform.modifiers.service.ScoreAdjustmentService;
import com.arcx.ctfplatform.users.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public ResponseEntity<List<ScoreAdjustmentResponse>> getAllAdjustments(
            @AuthenticationPrincipal User user) {
        List<ScoreAdjustment> adjustments = scoreAdjustmentService.getAdjustmentsForCaller(user);
        List<ScoreAdjustmentResponse> response = scoreAdjustmentMapper.mappingList(adjustments);
        return ResponseEntity.ok(response);
    }

    public ResponseEntity<List<ScoreAdjustmentResponse>> getAllAdjustments() {
        return getAllAdjustments(null);
    }
}
