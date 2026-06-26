package com.arcx.ctfplatform.challenges.controller;

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

import com.arcx.ctfplatform.challenges.dto.CtfTaskRequest;
import com.arcx.ctfplatform.challenges.dto.CtfTaskResponse;
import com.arcx.ctfplatform.challenges.service.CtfTaskService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/challenges")
@RequiredArgsConstructor
public class AdminCtfTaskController {

    private final CtfTaskService ctfTaskService;

    @GetMapping
    public ResponseEntity<List<CtfTaskResponse>> getAllTasks() {
        return ResponseEntity.ok(ctfTaskService.getAllTasks());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CtfTaskResponse> getTaskById(@PathVariable UUID id) {
        return ResponseEntity.ok(ctfTaskService.getTaskById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TEACHER')")
    public ResponseEntity<CtfTaskResponse> createTask(@Valid @RequestBody CtfTaskRequest request) {
        CtfTaskResponse response = ctfTaskService.createTask(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TEACHER')")
    public ResponseEntity<CtfTaskResponse> updateTask(@PathVariable UUID id, @Valid @RequestBody CtfTaskRequest request) {
        return ResponseEntity.ok(ctfTaskService.updateTask(id, request));
    }

    @GetMapping("/{id}/flag")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TEACHER')")
    public ResponseEntity<java.util.Map<String, String>> getTaskFlag(@PathVariable UUID id) {
        return ResponseEntity.ok(java.util.Map.of("flag", ctfTaskService.getTaskFlag(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TEACHER')")
    public ResponseEntity<Void> deleteTask(@PathVariable UUID id) {
        ctfTaskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }
}
