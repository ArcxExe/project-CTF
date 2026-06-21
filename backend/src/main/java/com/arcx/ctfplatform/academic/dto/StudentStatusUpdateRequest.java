package com.arcx.ctfplatform.academic.dto;

import com.arcx.ctfplatform.academic.entity.StudentStatus;
import jakarta.validation.constraints.NotNull;

public record StudentStatusUpdateRequest(
        @NotNull StudentStatus status
) {
}
