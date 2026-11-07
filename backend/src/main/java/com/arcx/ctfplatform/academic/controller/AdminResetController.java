package com.arcx.ctfplatform.academic.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arcx.ctfplatform.academic.dto.ResetRequestDto;
import com.arcx.ctfplatform.academic.dto.ResetResultDto;
import com.arcx.ctfplatform.academic.service.AcademicResetService;
import com.arcx.ctfplatform.users.entity.User;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/academic")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminResetController {

    private final AcademicResetService academicResetService;

    @PostMapping("/reset")
    public ResponseEntity<ResetResultDto> resetSemester(
            @Valid @RequestBody ResetRequestDto request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(academicResetService.resetSemester(request, currentUser));
    }
}
