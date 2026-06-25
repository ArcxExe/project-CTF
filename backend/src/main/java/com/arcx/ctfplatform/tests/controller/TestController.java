package com.arcx.ctfplatform.tests.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arcx.ctfplatform.attempts.dto.ChallengeSubmitResponse;
import com.arcx.ctfplatform.attempts.dto.SubmitFlagRequest;
import com.arcx.ctfplatform.challenges.dto.CtfTaskResponse;
import com.arcx.ctfplatform.tests.dto.TestResponse;
import com.arcx.ctfplatform.tests.service.TestService;
import com.arcx.ctfplatform.users.entity.User;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tests")
@RequiredArgsConstructor
public class TestController {

    private final TestService testService;

    @GetMapping
    public ResponseEntity<List<TestResponse>> getPublishedTests() {
        return ResponseEntity.ok(testService.getPublishedTests());
    }

    @GetMapping("/{testId}/challenges")
    public ResponseEntity<List<CtfTaskResponse>> getChallengesForTest(@PathVariable UUID testId) {
        return ResponseEntity.ok(testService.getChallengesForTest(testId));
    }

    @PostMapping("/{testId}/challenges/{challengeId}/submit")
    public ResponseEntity<ChallengeSubmitResponse> submitChallengeFlag(
            @PathVariable UUID testId,
            @PathVariable UUID challengeId,
            @Valid @RequestBody SubmitFlagRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(testService.submitChallengeFlag(testId, challengeId, request.flag(), user));
    }
}
