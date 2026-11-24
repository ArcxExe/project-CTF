package com.arcx.ctfplatform.students.controller.lab;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<List<LabScoreResponse>> getAllScores(
            @RequestParam(defaultValue = "GLOBAL") String scopeType,
            @RequestParam(required = false) UUID scopeId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Access Denied");
        }
        if (isAdminOrInstructor(auth)) {
            return ResponseEntity.ok(labScoreService.getAllLabScores(scopeType, scopeId));
        }
        if (hasRole(auth, "STUDENT")) {
            UUID myStudentId = labScoreService.resolveStudentIdForAuth(auth);
            if (myStudentId == null) {
                return ResponseEntity.ok(List.of());
            }
            return ResponseEntity.ok(
                    labScoreService.getLabScoreForStudent(myStudentId, scopeType, scopeId)
                            .map(List::of)
                            .orElseGet(List::of)
            );
        }
        throw new AccessDeniedException("Access Denied");
    }

    private boolean isAdminOrInstructor(Authentication auth) {
        return hasRole(auth, "ADMIN") || hasRole(auth, "INSTRUCTOR");
    }

    private boolean hasRole(Authentication auth, String roleName) {
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        String prefixed = "ROLE_" + roleName;
        return auth.getAuthorities().stream()
                .filter(a -> a != null && a.getAuthority() != null)
                .anyMatch(a -> prefixed.equalsIgnoreCase(a.getAuthority())
                        || roleName.equalsIgnoreCase(a.getAuthority()));
    }
}
