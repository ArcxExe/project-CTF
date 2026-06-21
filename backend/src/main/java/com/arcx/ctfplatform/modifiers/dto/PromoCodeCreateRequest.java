package com.arcx.ctfplatform.modifiers.dto;

import com.arcx.ctfplatform.modifiers.entity.PromoModifierType;

public record PromoCodeCreateRequest(
    String code,
    PromoModifierType modifierType,
    Integer value
) {}
