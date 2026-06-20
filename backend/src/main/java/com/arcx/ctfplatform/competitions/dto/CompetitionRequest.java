package com.arcx.ctfplatform.competitions.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.arcx.ctfplatform.competitions.entity.CompetitionStatus;

public record CompetitionRequest(
        @NotBlank String title,
        String description,
        Instant startsAt,
        Instant endsAt,
        @NotNull CompetitionStatus status
) {
}
