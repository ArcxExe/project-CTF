package com.arcx.ctfplatform.tests.dto;

import java.util.List;
import java.util.UUID;

public record QuizQuestionStudentResponse(
        UUID id,
        UUID testId,
        String type,
        String text,
        Integer points,
        Integer ordering,
        List<QuizOptionStudentResponse> options
) {
}
