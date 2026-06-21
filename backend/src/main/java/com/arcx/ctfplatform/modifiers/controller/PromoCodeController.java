package com.arcx.ctfplatform.modifiers.controller;

import com.arcx.ctfplatform.modifiers.dto.RedeemPromoCodeResponse;
import com.arcx.ctfplatform.modifiers.entity.PromoCode;
import com.arcx.ctfplatform.modifiers.entity.PromoModifierType;
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

    @PostMapping("/redeem")
    public ResponseEntity<RedeemPromoCodeResponse> redeemPromoCode(
            @RequestBody ClaimRequest request,
            @AuthenticationPrincipal User user) {
        
        PromoCode promo = promoCodeService.claimCode(user.getId(), request.code());
        
        int bonusPoints = (promo.getModifierType() == PromoModifierType.FIXED_ADD) ? promo.getValue() : 0;
        String msg = "Промокод успешно активирован!";
        if (promo.getModifierType() == PromoModifierType.FIXED_ADD) {
            msg = "Промокод активирован: +" + promo.getValue() + " баллов!";
        } else if (promo.getModifierType() == PromoModifierType.DOUBLE_COEFF) {
            msg = "Промокод активирован: ваши баллы удвоены!";
        }
        
        return ResponseEntity.ok(new RedeemPromoCodeResponse(true, msg, bonusPoints, 0));
    }
}
