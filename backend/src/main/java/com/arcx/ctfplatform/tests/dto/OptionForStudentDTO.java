package com.arcx.ctfplatform.tests.dto;

import java.util.UUID;

public record OptionForStudentDTO(
        UUID id,
        UUID questionId,
        String optionText,
        Integer sequenceOrder
) {
}
