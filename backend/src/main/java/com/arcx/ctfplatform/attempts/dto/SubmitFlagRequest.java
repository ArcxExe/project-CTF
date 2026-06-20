package com.arcx.ctfplatform.attempts.dto;

import jakarta.validation.constraints.NotBlank;

public record SubmitFlagRequest(
        @NotBlank String flag
) {
}
