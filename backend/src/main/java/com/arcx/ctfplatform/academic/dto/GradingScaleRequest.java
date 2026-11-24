package com.arcx.ctfplatform.academic.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

public record GradingScaleRequest(
        @DecimalMin(value = "0.00", message = "Minimum score must be at least 0")
        @DecimalMax(value = "100.00", message = "Minimum score must not exceed 100")
        BigDecimal minCoefficient,
        @DecimalMin(value = "0.00", message = "Maximum score must be at least 0")
        @DecimalMax(value = "100.00", message = "Maximum score must not exceed 100")
        BigDecimal maxCoefficient,
        @Min(value = 2, message = "Grade must be between 2 and 5")
        @Max(value = 5, message = "Grade must be between 2 and 5")
        Integer grade,
        @Size(max = 150, message = "Description must not exceed 150 characters")
        String description,
        UUID flowId,
        @Size(max = 64, message = "Grade name must not exceed 64 characters")
        String gradeName,
        @DecimalMin(value = "0.00", message = "Minimum score must be at least 0")
        @DecimalMax(value = "100.00", message = "Minimum score must not exceed 100")
        BigDecimal minScore,
        @DecimalMin(value = "0.00", message = "Maximum score must be at least 0")
        @DecimalMax(value = "100.00", message = "Maximum score must not exceed 100")
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
        return minScore;
    }

    public BigDecimal effectiveMaxCoefficient() {
        if (maxCoefficient != null) return maxCoefficient;
        return maxScore;
    }

    public String effectiveGradeName() {
        if (gradeName != null && !gradeName.isBlank()) return gradeName.trim();
        if (grade != null) return String.valueOf(grade);
        if (description != null && !description.isBlank()) return description.trim();
        return null;
    }

    public Integer effectiveGrade() {
        if (grade != null) {
            if (grade < 2 || grade > 5) {
                throw new IllegalArgumentException("Grade must be between 2 and 5");
            }
            return grade;
        }
        String name = effectiveGradeName();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Grade or gradeName is required");
        }
        try {
            int parsed = Integer.parseInt(name);
            if (parsed < 2 || parsed > 5) {
                throw new IllegalArgumentException("Grade must be between 2 and 5");
            }
            return parsed;
        } catch (NumberFormatException e) {
            return switch (name.toUpperCase(Locale.ROOT)) {
                case "A", "ОТЛИЧНО", "ОТЛ", "ОТЛ." -> 5;
                case "B", "C", "ХОРОШО", "ХОР", "ХОР." -> 4;
                case "D", "E", "УДОВЛ", "УДОВЛ.", "УДОВЛЕТВОРИТЕЛЬНО" -> 3;
                case "F", "НЕУД", "НЕУД.", "НЕУДОВЛЕТВОРИТЕЛЬНО" -> 2;
                default -> throw new IllegalArgumentException(
                        "Unsupported grade name: " + name + ". Allowed: 2–5, A/B/C/D/F, Отлично/Хорошо/Удовл/Неуд"
                );
            };
        }
    }

    public String effectiveDescription() {
        if (description != null && !description.isBlank()) return description.trim();
        String name = effectiveGradeName();
        return name != null ? name : "";
    }
}
