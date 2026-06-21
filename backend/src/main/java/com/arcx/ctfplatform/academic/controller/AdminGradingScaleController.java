package com.arcx.ctfplatform.academic.controller;

import com.arcx.ctfplatform.academic.dto.GradingScaleRequest;
import com.arcx.ctfplatform.academic.dto.GradingScaleResponse;
import com.arcx.ctfplatform.academic.entity.GradingScale;
import com.arcx.ctfplatform.academic.repository.GradingScaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/grading-scales")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminGradingScaleController {

    private final GradingScaleRepository gradingScaleRepository;

    @GetMapping
    public ResponseEntity<List<GradingScaleResponse>> getAll() {
        List<GradingScaleResponse> scales = gradingScaleRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(scales);
    }

    @PostMapping
    public ResponseEntity<GradingScaleResponse> create(@RequestBody GradingScaleRequest request) {
        GradingScale scale = GradingScale.builder()
                .minCoefficient(request.minCoefficient())
                .maxCoefficient(request.maxCoefficient())
                .grade(request.grade())
                .description(request.description())
                .build();
        GradingScale saved = gradingScaleRepository.save(scale);
        return ResponseEntity.ok(mapToResponse(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GradingScaleResponse> update(@PathVariable UUID id, @RequestBody GradingScaleRequest request) {
        GradingScale scale = gradingScaleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scale not found"));

        scale.setMinCoefficient(request.minCoefficient());
        scale.setMaxCoefficient(request.maxCoefficient());
        scale.setGrade(request.grade());
        scale.setDescription(request.description());

        GradingScale saved = gradingScaleRepository.save(scale);
        return ResponseEntity.ok(mapToResponse(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        gradingScaleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private GradingScaleResponse mapToResponse(GradingScale scale) {
        return new GradingScaleResponse(
                scale.getId(),
                scale.getMinCoefficient(),
                scale.getMaxCoefficient(),
                scale.getGrade(),
                scale.getDescription()
        );
    }
}
