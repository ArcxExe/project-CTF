package com.arcx.ctfplatform.academic.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arcx.ctfplatform.academic.dto.AcademicFlowRequestDTO;
import com.arcx.ctfplatform.academic.dto.AcademicFlowResponseDTO;
import com.arcx.ctfplatform.academic.service.AcademicFlowService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/streams")
@RequiredArgsConstructor
public class AcademicFlowController {

    private final AcademicFlowService flowService;

    @GetMapping
    public ResponseEntity<List<AcademicFlowResponseDTO>> getAllFlows() {
        return ResponseEntity.ok(flowService.getAllFlows());
    }

    @PostMapping
    public ResponseEntity<AcademicFlowResponseDTO> createFlow(@Valid @RequestBody AcademicFlowRequestDTO request) {
        return new ResponseEntity<>(flowService.createFlow(request), HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlow(@PathVariable UUID id) {
        flowService.deleteFlow(id);
        return ResponseEntity.noContent().build();
    }
}
