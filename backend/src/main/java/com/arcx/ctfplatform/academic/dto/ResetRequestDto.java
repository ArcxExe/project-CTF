package com.arcx.ctfplatform.academic.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetRequestDto {

    @NotNull(message = "Режим очистки обязателен")
    private ResetMode mode;

    private UUID streamId;

    private UUID groupId;

    @NotBlank(message = "Фраза подтверждения обязательна")
    private String confirmationPhrase;
}
