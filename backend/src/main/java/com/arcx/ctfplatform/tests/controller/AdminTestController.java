package com.arcx.ctfplatform.tests.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arcx.ctfplatform.challenges.dto.ChallengeResponse;
import com.arcx.ctfplatform.tests.dto.TestRequest;
import com.arcx.ctfplatform.tests.dto.TestResponse;
import com.arcx.ctfplatform.tests.service.TestService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/tests")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminTestController {

    private final TestService testService;

    @GetMapping
    public ResponseEntity<List<TestResponse>> getAllTests() {
        return ResponseEntity.ok(testService.getAllTests());
    }

    @PostMapping
    public ResponseEntity<TestResponse> createTest(@Valid @RequestBody TestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(testService.createTest(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TestResponse> updateTest(@PathVariable UUID id, @Valid @RequestBody TestRequest request) {
        return ResponseEntity.ok(testService.updateTest(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTest(@PathVariable UUID id) {
        testService.deleteTest(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{testId}/challenges")
    public ResponseEntity<List<ChallengeResponse>> getChallengesForTest(@PathVariable UUID testId) {
        return ResponseEntity.ok(testService.getChallengesForTest(testId));
    }

    @PostMapping("/{testId}/challenges/{challengeId}")
    public ResponseEntity<Void> addChallengeToTest(@PathVariable UUID testId, @PathVariable UUID challengeId) {
        testService.addChallengeToTest(testId, challengeId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{testId}/challenges/{challengeId}")
    public ResponseEntity<Void> removeChallengeFromTest(@PathVariable UUID testId, @PathVariable UUID challengeId) {
        testService.removeChallengeFromTest(testId, challengeId);
        return ResponseEntity.noContent().build();
    }
}
