package com.arcx.ctfplatform.students.controller.lab;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.arcx.ctfplatform.students.dto.lab.LabScoreResponse;
import com.arcx.ctfplatform.students.service.lab.LabScoreService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/lab-scores")
@RequiredArgsConstructor
public class LabScoreController {

    private final LabScoreService labScoreService;

    @GetMapping
    public ResponseEntity<List<LabScoreResponse>> getAllScores(
            @RequestParam(defaultValue = "GLOBAL") String scopeType,
            @RequestParam(required = false) UUID scopeId) {
        return ResponseEntity.ok(labScoreService.getAllLabScores(scopeType, scopeId));
    }
}
