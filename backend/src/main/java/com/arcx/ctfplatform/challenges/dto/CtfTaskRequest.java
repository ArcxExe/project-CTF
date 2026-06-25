package com.arcx.ctfplatform.challenges.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

public record CtfTaskRequest(
        @NotBlank @Size(max = 200) String title,
        String description,
        String category,
        String difficulty,
        @NotNull @Min(1) Integer baseScore,
        @NotBlank String flag
) {
}
