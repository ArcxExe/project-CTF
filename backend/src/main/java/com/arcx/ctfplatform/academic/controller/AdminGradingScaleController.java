package com.arcx.ctfplatform.academic.controller;

import com.arcx.ctfplatform.academic.dto.GradingScaleRequest;
import com.arcx.ctfplatform.academic.dto.GradingScaleResponse;
import com.arcx.ctfplatform.academic.entity.GradingScale;
import com.arcx.ctfplatform.academic.repository.AcademicFlowRepository;
import com.arcx.ctfplatform.academic.repository.GradingScaleRepository;
import com.arcx.ctfplatform.audit.service.AuditService;
import com.arcx.ctfplatform.users.entity.User;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/grading-scales")
@PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
public class AdminGradingScaleController {

    private static final BigDecimal MIN_ALLOWED_BOUND = BigDecimal.ZERO;
    private static final BigDecimal MAX_ALLOWED_BOUND = BigDecimal.valueOf(100);

    private final GradingScaleRepository gradingScaleRepository;
    private final AuditService auditService;
    private final AcademicFlowRepository academicFlowRepository;

    public AdminGradingScaleController(GradingScaleRepository gradingScaleRepository) {
        this(gradingScaleRepository, null, null);
    }

    public AdminGradingScaleController(
            GradingScaleRepository gradingScaleRepository,
            AuditService auditService
    ) {
        this(gradingScaleRepository, auditService, null);
    }

    @Autowired
    public AdminGradingScaleController(
            GradingScaleRepository gradingScaleRepository,
            @Autowired(required = false) AuditService auditService,
            @Autowired(required = false) AcademicFlowRepository academicFlowRepository
    ) {
        this.gradingScaleRepository = gradingScaleRepository;
        this.auditService = auditService;
        this.academicFlowRepository = academicFlowRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
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
    @Transactional
    public ResponseEntity<GradingScaleResponse> create(
            @Valid @RequestBody GradingScaleRequest request,
            @AuthenticationPrincipal User user
    ) {
        ValidatedScale validated = validateRequest(request);
        GradingScale scale = GradingScale.builder()
                .minCoefficient(validated.min())
                .maxCoefficient(validated.max())
                .grade(validated.grade())
                .description(validated.description())
                .flowId(request.flowId())
                .gradeName(validated.gradeName())
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
    @Transactional
    public ResponseEntity<GradingScaleResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody GradingScaleRequest request,
            @AuthenticationPrincipal User user
    ) {
        GradingScale scale = gradingScaleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scale not found"));
        ValidatedScale validated = validateRequest(request);

        String oldVal = scale.getMinCoefficient() + ".." + scale.getMaxCoefficient() + " -> " + scale.getGrade();

        scale.setMinCoefficient(validated.min());
        scale.setMaxCoefficient(validated.max());
        scale.setGrade(validated.grade());
        scale.setDescription(validated.description());
        scale.setFlowId(request.flowId());
        scale.setGradeName(validated.gradeName());

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
    @Transactional
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

    private ValidatedScale validateRequest(GradingScaleRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        BigDecimal min = request.effectiveMinCoefficient();
        BigDecimal max = request.effectiveMaxCoefficient();
        if (min == null || max == null) {
            throw new IllegalArgumentException("minCoefficient and maxCoefficient are required");
        }
        if (min.compareTo(MIN_ALLOWED_BOUND) < 0
                || max.compareTo(MIN_ALLOWED_BOUND) < 0
                || min.compareTo(MAX_ALLOWED_BOUND) > 0
                || max.compareTo(MAX_ALLOWED_BOUND) > 0) {
            throw new IllegalArgumentException("Score range must be between 0 and 100");
        }
        if (min.compareTo(max) > 0) {
            throw new IllegalArgumentException("minCoefficient cannot be greater than maxCoefficient");
        }
        Integer grade = request.effectiveGrade();
        String gradeName = request.effectiveGradeName();
        if (gradeName == null || gradeName.isBlank()) {
            gradeName = String.valueOf(grade);
        }
        if (gradeName.length() > 64) {
            throw new IllegalArgumentException("Grade name must not exceed 64 characters");
        }
        String description = request.effectiveDescription();
        if (description.length() > 150) {
            throw new IllegalArgumentException("Description must not exceed 150 characters");
        }
        if (request.flowId() != null && academicFlowRepository != null
                && !academicFlowRepository.existsById(request.flowId())) {
            throw new IllegalArgumentException("Academic flow not found");
        }
        return new ValidatedScale(min, max, grade, gradeName, description);
    }

    private record ValidatedScale(
            BigDecimal min,
            BigDecimal max,
            Integer grade,
            String gradeName,
            String description
    ) {}

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
