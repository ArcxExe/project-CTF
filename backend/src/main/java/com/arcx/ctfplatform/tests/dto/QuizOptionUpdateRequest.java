package com.arcx.ctfplatform.tests.dto;

import java.util.UUID;

public record QuizOptionUpdateRequest(
        String optionText,
        Boolean isCorrect,
        Boolean correct,
        Integer sequenceOrder,
        UUID questionId
) {
    public boolean hasCorrectFlag() {
        return isCorrect != null || correct != null;
    }

    public boolean resolvedCorrect() {
        return isCorrect != null ? isCorrect : Boolean.TRUE.equals(correct);
    }
}
