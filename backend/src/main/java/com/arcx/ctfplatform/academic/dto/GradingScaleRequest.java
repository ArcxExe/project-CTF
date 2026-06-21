package com.arcx.ctfplatform.academic.dto;

import java.math.BigDecimal;

public record GradingScaleRequest(
        BigDecimal minCoefficient,
        BigDecimal maxCoefficient,
        Integer grade,
        String description
) {
}
