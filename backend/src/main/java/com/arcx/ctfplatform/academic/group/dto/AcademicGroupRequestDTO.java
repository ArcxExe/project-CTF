package com.arcx.ctfplatform.academic.group.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;

public record AcademicGroupRequestDTO(
    @NotBlank(message = "Название группы обязательно") String name,
    UUID streamId,
    Integer maxLabs
    ) {
}

