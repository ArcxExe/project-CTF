package com.arcx.ctfplatform.academic.controller;

import java.io.IOException;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.arcx.ctfplatform.academic.dto.StudentResponse;
import com.arcx.ctfplatform.academic.dto.StudentStatusUpdateRequest;
import com.arcx.ctfplatform.academic.dto.StudentCreateRequest;
import com.arcx.ctfplatform.academic.service.StudentImportService;
import com.arcx.ctfplatform.academic.service.StudentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/admin/students")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminStudentController {

    private final StudentImportService studentImportService;
    private final StudentService studentService;

    @PostMapping
    public ResponseEntity<StudentResponse> createStudent(@Valid @RequestBody StudentCreateRequest request) {
        return ResponseEntity.ok(studentService.createStudent(request));
    }

    @PostMapping("/import")
    public ResponseEntity<Void> importStudents(@RequestParam("file") MultipartFile file) throws IOException {
        studentImportService.parseAndImportStudents(file.getInputStream());
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<Page<StudentResponse>> getStudents(
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String studentCode,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(studentService.getStudents(firstName, lastName, studentCode, pageable));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<StudentResponse> updateStudentStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StudentStatusUpdateRequest request) {
        return ResponseEntity.ok(studentService.updateStatus(id, request.status()));
    }

    @GetMapping("/pending-bindings")
    public ResponseEntity<Page<StudentResponse>> getPendingBindings(
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(studentService.getPendingBindings(pageable));
    }

    @PostMapping("/{id}/approve-binding")
    public ResponseEntity<Void> approveBinding(@PathVariable UUID id) {
        studentService.approveBinding(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reject-binding")
    public ResponseEntity<Void> rejectBinding(@PathVariable UUID id) {
        studentService.rejectBinding(id);
        return ResponseEntity.ok().build();
    }
}
