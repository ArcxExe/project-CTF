package com.arcx.ctfplatform.academic.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record GradingScaleRequest(
        BigDecimal minCoefficient,
        BigDecimal maxCoefficient,
        Integer grade,
        String description,
        UUID flowId,
        String gradeName,
        BigDecimal minScore,
        BigDecimal maxScore
) {
    public GradingScaleRequest(
            BigDecimal minCoefficient,
            BigDecimal maxCoefficient,
            Integer grade,
            String description
    ) {
        this(minCoefficient, maxCoefficient, grade, description, null, null, minCoefficient, maxCoefficient);
    }

    public BigDecimal effectiveMinCoefficient() {
        if (minCoefficient != null) return minCoefficient;
        if (minScore != null) return minScore;
        return BigDecimal.ZERO;
    }

    public BigDecimal effectiveMaxCoefficient() {
        if (maxCoefficient != null) return maxCoefficient;
        if (maxScore != null) return maxScore;
        return BigDecimal.valueOf(100);
    }

    public String effectiveGradeName() {
        if (gradeName != null && !gradeName.isBlank()) return gradeName.trim();
        if (grade != null) return String.valueOf(grade);
        if (description != null && !description.isBlank()) return description.trim();
        return "5";
    }

    public Integer effectiveGrade() {
        if (grade != null) return grade;
        String name = effectiveGradeName();
        try {
            return Integer.parseInt(name);
        } catch (NumberFormatException e) {
            return switch (name.toUpperCase()) {
                case "A", "ОТЛИЧНО" -> 5;
                case "B", "C", "ХОРОШО" -> 4;
                case "D", "E", "УДОВЛ", "УДОВЛЕТВОРИТЕЛЬНО" -> 3;
                case "F", "НЕУД", "НЕУДОВЛЕТВОРИТЕЛЬНО" -> 2;
                default -> 5;
            };
        }
    }

    public String effectiveDescription() {
        if (description != null && !description.isBlank()) return description.trim();
        return effectiveGradeName();
    }
}
