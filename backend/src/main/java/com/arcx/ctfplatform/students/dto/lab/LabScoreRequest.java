package com.arcx.ctfplatform.students.dto.lab;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LabScoreRequest(
    @NotNull(message = "Student ID cannot be null")
    UUID studentId,

    @Min(value = 0, message = "Score must be non-negative")
    int score,

    @NotBlank(message = "Reason cannot be blank")
    String reason
) {
}
