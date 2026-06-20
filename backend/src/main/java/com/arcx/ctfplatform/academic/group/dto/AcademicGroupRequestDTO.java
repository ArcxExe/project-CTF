package com.arcx.ctfplatform.academic.group.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AcademicGroupRequestDTO(
    @NotBlank String name,
    @NotNull UUID streamId
    ) {
}

