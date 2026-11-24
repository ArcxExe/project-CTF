package com.arcx.ctfplatform.tests.dto;

import jakarta.validation.constraints.NotBlank;

public record QuizOptionRequest(
        @NotBlank(message = "Option text is required") String optionText,
        Boolean isCorrect,
        Boolean correct,
        Integer sequenceOrder
) {
    public boolean resolvedCorrect() {
        return isCorrect != null ? isCorrect : Boolean.TRUE.equals(correct);
    }
}
