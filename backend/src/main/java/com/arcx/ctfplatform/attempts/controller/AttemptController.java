package com.arcx.ctfplatform.attempts.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arcx.ctfplatform.attempts.dto.ChallengeSubmitResponse;
import com.arcx.ctfplatform.attempts.dto.SubmitFlagRequest;
import com.arcx.ctfplatform.attempts.service.AttemptService;
import com.arcx.ctfplatform.users.entity.User;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/challenges")
@RequiredArgsConstructor
public class AttemptController {

    private final AttemptService attemptService;

    @PostMapping("/{id}/submit")
    public ResponseEntity<ChallengeSubmitResponse> submitFlag(
            @PathVariable UUID id,
            @Valid @RequestBody SubmitFlagRequest request,
            @AuthenticationPrincipal User user) {

        ChallengeSubmitResponse response = attemptService.submitFlag(user.getId(), id, request.flag());
        return ResponseEntity.ok(response);
    }
}
