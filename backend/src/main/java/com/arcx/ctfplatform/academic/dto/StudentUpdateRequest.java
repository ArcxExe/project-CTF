package com.arcx.ctfplatform.academic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StudentUpdateRequest(
    @NotBlank(message = "Имя обязательно для заполнения")
    String firstName,

    @NotBlank(message = "Фамилия обязательна для заполнения")
    String lastName,

    String middleName,

    @NotBlank(message = "Код студента обязателен для заполнения")
    String studentCode,

    @NotNull(message = "Группа обязательна для выбора")
    UUID groupId
) {}
