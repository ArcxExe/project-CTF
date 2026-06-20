package com.arcx.ctfplatform.modifiers.controller;

import com.arcx.ctfplatform.modifiers.entity.PromoCode;
import com.arcx.ctfplatform.modifiers.service.PromoCodeService;
import com.arcx.ctfplatform.users.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/promo-codes")
@RequiredArgsConstructor
public class PromoCodeController {

    private final PromoCodeService promoCodeService;

    public record ClaimRequest(String code) {}

    @PostMapping("/claim")
    public ResponseEntity<PromoCode> claimPromoCode(
            @RequestBody ClaimRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(promoCodeService.claimCode(user.getId(), request.code()));
    }
}
