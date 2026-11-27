package com.arcx.ctfplatform.academic.group.dto;

import java.util.UUID;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcademicGroupRequestDTO(
    @NotBlank(message = "Название группы обязательно")
    @Size(max = 50, message = "Название группы не должно превышать 50 символов")
    String name,
    UUID streamId,
    @Min(value = 0, message = "Количество лабораторных работ не может быть отрицательным")
    Integer maxLabs
    ) {
}

