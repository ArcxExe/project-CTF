package com.arcx.ctfplatform.challenges.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

public record ChallengeRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String description,
        @NotNull @Min(1) Integer points,
        @NotBlank String flag,
        @NotNull UUID competitionId
) {
}
