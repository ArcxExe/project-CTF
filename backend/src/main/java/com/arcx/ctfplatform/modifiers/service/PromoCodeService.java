package com.arcx.ctfplatform.modifiers.service;

import com.arcx.ctfplatform.modifiers.entity.PromoCode;
import com.arcx.ctfplatform.modifiers.entity.PromoCodeClaim;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeRepository;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeClaimRepository;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.arcx.ctfplatform.leaderboard.service.LeaderboardService;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromoCodeService {

    private final PromoCodeRepository promoCodeRepository;
    private final PromoCodeClaimRepository promoCodeClaimRepository;
    private final StudentRepository studentRepository;
    private final LeaderboardService leaderboardService;

    @Transactional
    public PromoCode claimCode(UUID userId, String code) {
        Student student = studentRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        PromoCode promo = promoCodeRepository.findByCodeWithLock(code)
                .orElseThrow(() -> new IllegalArgumentException("Invalid promo code"));

        if (promoCodeClaimRepository.existsByPromoCodeIdAndStudentId(promo.getId(), student.getId())) {
            throw new IllegalStateException("Promo code already used by this student");
        }

        if (promo.getUsedCount() >= promo.getMaxUses()) {
            throw new IllegalStateException("Promo code usage limit reached");
        }

        promo.setUsedCount(promo.getUsedCount() + 1);
        PromoCode saved = promoCodeRepository.save(promo);

        PromoCodeClaim claim = PromoCodeClaim.builder()
                .promoCode(saved)
                .studentId(student.getId())
                .claimedAt(Instant.now())
                .build();
        promoCodeClaimRepository.save(claim);

        leaderboardService.broadcastUpdate();
        return saved;
    }
}

