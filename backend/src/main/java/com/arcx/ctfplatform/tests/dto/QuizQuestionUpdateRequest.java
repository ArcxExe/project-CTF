package com.arcx.ctfplatform.tests.dto;

import jakarta.validation.constraints.Min;

import java.util.UUID;

public record QuizQuestionUpdateRequest(
        String type,
        String text,
        @Min(value = 0, message = "Points must be non-negative") Integer points,
        @Min(value = 0, message = "Ordering must be non-negative") Integer ordering,
        UUID testId
) {
}
