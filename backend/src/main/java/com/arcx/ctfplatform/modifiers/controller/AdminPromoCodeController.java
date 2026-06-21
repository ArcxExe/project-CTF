package com.arcx.ctfplatform.modifiers.controller;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arcx.ctfplatform.modifiers.dto.PromoCodeCreateRequest;
import com.arcx.ctfplatform.modifiers.dto.PromoCodeResponse;
import com.arcx.ctfplatform.modifiers.entity.PromoCode;
import com.arcx.ctfplatform.modifiers.entity.PromoModifierType;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/promo-codes")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPromoCodeController {

    private final PromoCodeRepository promoCodeRepository;

    @GetMapping
    public ResponseEntity<List<PromoCodeResponse>> getAllPromoCodes() {
        List<PromoCodeResponse> responseList = promoCodeRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responseList);
    }

    @PostMapping
    public ResponseEntity<PromoCodeResponse> createPromoCode(@RequestBody PromoCodeCreateRequest request) {
        PromoModifierType modifierType = PromoModifierType.FIXED_ADD;
        int value = request.bonusPoints() != null ? request.bonusPoints() : 0;

        PromoCode promo = PromoCode.builder()
                .code(request.code().trim().toUpperCase())
                .modifierType(modifierType)
                .value(value)
                .isUsed(false)
                .build();

        PromoCode saved = promoCodeRepository.save(promo);
        return ResponseEntity.ok(toResponse(saved));
    }

    private PromoCodeResponse toResponse(PromoCode promo) {
        int bonusPoints = (promo.getModifierType() == PromoModifierType.FIXED_ADD) ? promo.getValue() : 0;
        return new PromoCodeResponse(
                promo.getId(),
                promo.getCode(),
                promo.getCode(), // title
                "Modifier: " + promo.getModifierType().name(), // description
                bonusPoints,
                0, // bonusAttempts
                1, // maxUses
                promo.isUsed() ? 1 : 0, // usedCount
                promo.isUsed() ? "EXPIRED" : "ACTIVE", // status
                null, // expiresAt
                promo.getUsedAt() != null ? promo.getUsedAt() : Instant.now(), // createdAt
                Instant.now() // updatedAt
        );
    }
}
