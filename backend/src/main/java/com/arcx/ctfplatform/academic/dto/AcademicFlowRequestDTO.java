package com.arcx.ctfplatform.academic.dto;

import jakarta.validation.constraints.NotBlank;

public record AcademicFlowRequestDTO(
    @NotBlank String name
) {}
