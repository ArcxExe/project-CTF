package com.arcx.ctfplatform.students.controller.lab;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        List<LabScoreResponse> scores = labScoreService.getAllLabScores(scopeType, scopeId);
        if (auth != null && isStudentOnly(auth)) {
            UUID myStudentId = labScoreService.resolveStudentIdForAuth(auth);
            if (myStudentId == null) {
                return ResponseEntity.ok(List.of());
            }
            return ResponseEntity.ok(
                    scores.stream()
                            .filter(s -> myStudentId.equals(s.studentId()))
                            .toList()
            );
        }
        return ResponseEntity.ok(scores);
    }

    private boolean isStudentOnly(Authentication auth) {
        if (auth.getAuthorities() == null || auth.getAuthorities().isEmpty()) {
            return false;
        }
        boolean isAdminOrInstructor = auth.getAuthorities().stream()
                .anyMatch(a -> {
                    String role = a.getAuthority().toUpperCase();
                    return role.contains("ADMIN") || role.contains("INSTRUCTOR");
                });
        return !isAdminOrInstructor;
    }
}
