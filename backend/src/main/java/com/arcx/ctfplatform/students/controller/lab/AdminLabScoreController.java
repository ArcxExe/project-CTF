package com.arcx.ctfplatform.students.controller.lab;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.arcx.ctfplatform.students.dto.lab.LabScoreRequest;
import com.arcx.ctfplatform.students.dto.lab.LabScoreResponse;
import com.arcx.ctfplatform.students.service.lab.LabScoreService;
import com.arcx.ctfplatform.users.entity.User;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/lab-scores")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminLabScoreController {

    private final LabScoreService labScoreService;

    @PostMapping
    public ResponseEntity<Void> setScore(
            @Valid @RequestBody LabScoreRequest request,
            @AuthenticationPrincipal User user) {
        labScoreService.setScore(request.studentId(), request.score(), request.reason(), user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/import")
    public ResponseEntity<Void> importScores(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User user) throws IOException {
        labScoreService.importLabScores(file.getInputStream(), user.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<LabScoreResponse>> getAllScores(
            @RequestParam(defaultValue = "GLOBAL") String scopeType,
            @RequestParam(required = false) UUID scopeId) {
        return ResponseEntity.ok(labScoreService.getAllLabScores(scopeType, scopeId));
    }
}
