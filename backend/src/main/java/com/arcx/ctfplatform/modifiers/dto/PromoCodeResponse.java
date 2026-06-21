package com.arcx.ctfplatform.modifiers.dto;

import java.time.Instant;
import java.util.UUID;
import com.arcx.ctfplatform.modifiers.entity.PromoModifierType;

public record PromoCodeResponse(
    UUID id,
    String code,
    PromoModifierType modifierType,
    Integer value,
    boolean isUsed,
    UUID usedByStudentId,
    String usedByStudentName,
    Instant usedAt,
    Integer maxUses,
    Integer usedCount
) {}

