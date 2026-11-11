package com.arcx.ctfplatform.academic.controller;

import com.arcx.ctfplatform.academic.dto.GradingScaleRequest;
import com.arcx.ctfplatform.academic.dto.GradingScaleResponse;
import com.arcx.ctfplatform.academic.entity.GradingScale;
import com.arcx.ctfplatform.academic.repository.GradingScaleRepository;
import com.arcx.ctfplatform.audit.service.AuditService;
import com.arcx.ctfplatform.users.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/grading-scales")
@PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
public class AdminGradingScaleController {

    private final GradingScaleRepository gradingScaleRepository;
    private final AuditService auditService;

    public AdminGradingScaleController(GradingScaleRepository gradingScaleRepository) {
        this(gradingScaleRepository, null);
    }

    @Autowired
    public AdminGradingScaleController(
            GradingScaleRepository gradingScaleRepository,
            @Autowired(required = false) AuditService auditService
    ) {
        this.gradingScaleRepository = gradingScaleRepository;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<List<GradingScaleResponse>> getAll(
            @RequestParam(required = false) UUID flowId
    ) {
        List<GradingScale> all = gradingScaleRepository.findAll();
        if (flowId != null) {
            all = all.stream()
                    .filter(s -> flowId.equals(s.getFlowId()))
                    .toList();
        }
        List<GradingScaleResponse> scales = all.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(scales);
    }

    @PostMapping
    public ResponseEntity<GradingScaleResponse> create(
            @RequestBody GradingScaleRequest request,
            @AuthenticationPrincipal User user
    ) {
        GradingScale scale = GradingScale.builder()
                .minCoefficient(request.effectiveMinCoefficient())
                .maxCoefficient(request.effectiveMaxCoefficient())
                .grade(request.effectiveGrade())
                .description(request.effectiveDescription())
                .flowId(request.flowId())
                .gradeName(request.effectiveGradeName())
                .build();
        GradingScale saved = gradingScaleRepository.save(scale);
        if (auditService != null) {
            auditService.logEvent(
                    user != null ? user.getId() : null,
                    user != null ? user.getUsername() : "ADMIN",
                    user != null && user.getRole() != null ? user.getRole().name() : "ADMIN",
                    "GRADING_SCALE_CREATE",
                    "GRADING_SCALE:" + saved.getId(),
                    null,
                    saved.getMinCoefficient() + ".." + saved.getMaxCoefficient() + " -> " + saved.getGradeName(),
                    null
            );
        }
        return ResponseEntity.ok(mapToResponse(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GradingScaleResponse> update(
            @PathVariable UUID id,
            @RequestBody GradingScaleRequest request,
            @AuthenticationPrincipal User user
    ) {
        GradingScale scale = gradingScaleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scale not found"));

        String oldVal = scale.getMinCoefficient() + ".." + scale.getMaxCoefficient() + " -> " + scale.getGrade();

        scale.setMinCoefficient(request.effectiveMinCoefficient());
        scale.setMaxCoefficient(request.effectiveMaxCoefficient());
        scale.setGrade(request.effectiveGrade());
        scale.setDescription(request.effectiveDescription());
        scale.setFlowId(request.flowId());
        scale.setGradeName(request.effectiveGradeName());

        GradingScale saved = gradingScaleRepository.save(scale);
        if (auditService != null) {
            auditService.logEvent(
                    user != null ? user.getId() : null,
                    user != null ? user.getUsername() : "ADMIN",
                    user != null && user.getRole() != null ? user.getRole().name() : "ADMIN",
                    "GRADING_SCALE_UPDATE",
                    "GRADING_SCALE:" + saved.getId(),
                    oldVal,
                    saved.getMinCoefficient() + ".." + saved.getMaxCoefficient() + " -> " + saved.getGradeName(),
                    null
            );
        }
        return ResponseEntity.ok(mapToResponse(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user
    ) {
        gradingScaleRepository.deleteById(id);
        if (auditService != null) {
            auditService.logEvent(
                    user != null ? user.getId() : null,
                    user != null ? user.getUsername() : "ADMIN",
                    user != null && user.getRole() != null ? user.getRole().name() : "ADMIN",
                    "GRADING_SCALE_DELETE",
                    "GRADING_SCALE:" + id,
                    null,
                    "DELETED",
                    null
            );
        }
        return ResponseEntity.noContent().build();
    }

    private GradingScaleResponse mapToResponse(GradingScale scale) {
        BigDecimal min = scale.getMinCoefficient() != null ? scale.getMinCoefficient() : BigDecimal.ZERO;
        BigDecimal max = scale.getMaxCoefficient() != null ? scale.getMaxCoefficient() : BigDecimal.ZERO;
        String gradeName = scale.getGradeName();
        if (gradeName == null || gradeName.isBlank()) {
            gradeName = scale.getGrade() != null
                    ? String.valueOf(scale.getGrade())
                    : (scale.getDescription() != null ? scale.getDescription() : "");
        }
        return new GradingScaleResponse(
                scale.getId(),
                min,
                max,
                scale.getGrade(),
                scale.getDescription(),
                scale.getFlowId(),
                gradeName,
                min,
                max
        );
    }
}
