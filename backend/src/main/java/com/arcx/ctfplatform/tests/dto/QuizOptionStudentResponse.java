package com.arcx.ctfplatform.tests.dto;

import java.util.UUID;

public record QuizOptionStudentResponse(
        UUID id,
        UUID questionId,
        String optionText,
        Integer sequenceOrder
) {
}
