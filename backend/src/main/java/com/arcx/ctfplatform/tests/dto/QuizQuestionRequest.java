package com.arcx.ctfplatform.tests.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record QuizQuestionRequest(
        @NotBlank(message = "Question type is required") String type,
        @NotBlank(message = "Question text is required") String text,
        @NotNull(message = "Points are required") @Min(value = 0, message = "Points must be non-negative") Integer points,
        @NotNull(message = "Ordering is required") @Min(value = 0, message = "Ordering must be non-negative") Integer ordering,
        UUID testId
) {
}
