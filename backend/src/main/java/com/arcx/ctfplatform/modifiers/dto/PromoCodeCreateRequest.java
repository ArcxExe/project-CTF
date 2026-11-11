package com.arcx.ctfplatform.modifiers.dto;

import com.arcx.ctfplatform.modifiers.entity.PromoModifierType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PromoCodeCreateRequest(
    @NotBlank String code,
    @NotNull PromoModifierType modifierType,
    Integer value,
    Integer maxUses
) {}

