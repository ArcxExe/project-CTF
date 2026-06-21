package com.arcx.ctfplatform.academic.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record GradingScaleResponse(
        UUID id,
        BigDecimal minCoefficient,
        BigDecimal maxCoefficient,
        Integer grade,
        String description
) {
}
