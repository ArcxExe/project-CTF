package com.arcx.ctfplatform.academic.controller;

import com.arcx.ctfplatform.academic.dto.AdminProgressResponse;
import com.arcx.ctfplatform.academic.service.AdminProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/progress")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminProgressController {

    private final AdminProgressService adminProgressService;

    @GetMapping
    public ResponseEntity<List<AdminProgressResponse>> getProgress() {
        return ResponseEntity.ok(adminProgressService.getAdminProgress());
    }
}
