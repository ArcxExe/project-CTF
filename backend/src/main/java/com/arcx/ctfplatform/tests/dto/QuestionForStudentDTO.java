package com.arcx.ctfplatform.tests.dto;

import java.util.List;
import java.util.UUID;

public record QuestionForStudentDTO(
        UUID id,
        UUID quizId,
        String type,
        String text,
        Integer points,
        Integer ordering,
        List<OptionForStudentDTO> options
) {
}
