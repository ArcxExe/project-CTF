package com.arcx.ctfplatform.tests.dto;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.arcx.ctfplatform.tests.entity.TestStatus;

public record TestRequest(
        @NotBlank String title,
        String description,
        @NotNull TestStatus status,
        @NotNull @Min(1) Integer timeLimitMinutes,
        @NotNull @Min(0) Integer passingScore,
        @NotNull @Min(0) Integer questionsCount,
        UUID competitionId
) {
}
