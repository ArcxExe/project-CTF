package com.arcx.ctfplatform.academic.group.dto;

import java.util.UUID;

public record AcademicGroupRequestDTO(
    String name,
    UUID streamId
    ) {
}
