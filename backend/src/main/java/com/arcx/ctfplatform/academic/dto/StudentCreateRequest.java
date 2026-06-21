package com.arcx.ctfplatform.academic.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record StudentCreateRequest(
    @NotBlank String firstName,
    @NotBlank String lastName,
    String middleName,
    @NotBlank String studentCode,
    UUID groupId
) {}
