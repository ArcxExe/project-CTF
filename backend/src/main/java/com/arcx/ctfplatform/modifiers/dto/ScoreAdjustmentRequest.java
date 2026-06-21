package com.arcx.ctfplatform.modifiers.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public record ScoreAdjustmentRequest(
        @NotNull UUID studentId,
        UUID competitionId,
        @NotNull Integer points,
        String reason
) {
}
