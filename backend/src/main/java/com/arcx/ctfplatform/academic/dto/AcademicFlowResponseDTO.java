package com.arcx.ctfplatform.academic.dto;

import java.time.Instant;
import java.util.UUID;

public record AcademicFlowResponseDTO(
    UUID id,
    String name,
    String academicYear,
    long groupsCount,
    long studentsCount,
    Instant createdAt
) {}
