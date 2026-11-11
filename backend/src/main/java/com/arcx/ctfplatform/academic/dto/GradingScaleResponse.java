package com.arcx.ctfplatform.academic.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record GradingScaleResponse(
        UUID id,
        BigDecimal minCoefficient,
        BigDecimal maxCoefficient,
        Integer grade,
        String description,
        UUID flowId,
        String gradeName,
        BigDecimal minScore,
        BigDecimal maxScore
) {
    public GradingScaleResponse(
            UUID id,
            BigDecimal minCoefficient,
            BigDecimal maxCoefficient,
            Integer grade,
            String description
    ) {
        this(
                id,
                minCoefficient,
                maxCoefficient,
                grade,
                description,
                null,
                grade != null ? String.valueOf(grade) : description,
                minCoefficient,
                maxCoefficient
        );
    }
}
