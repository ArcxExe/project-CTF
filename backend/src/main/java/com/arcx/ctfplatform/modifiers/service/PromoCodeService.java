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
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Промокод не найден или введён неверно");
        }
        String normalizedCode = code.trim().toUpperCase();

        Student student = studentRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("Профиль студента не найден"));

        PromoCode promo = promoCodeRepository.findByCodeForUpdate(normalizedCode)
                .or(() -> promoCodeRepository.findByCode(normalizedCode))
                .orElseThrow(() -> new IllegalArgumentException("Промокод не найден или введён неверно"));

        if (promoCodeClaimRepository.existsByPromoCodeIdAndStudentId(promo.getId(), student.getId())) {
            throw new IllegalStateException("Вы уже использовали этот промокод ранее");
        }

        if (promo.getUsedCount() >= promo.getMaxUses()) {
            throw new IllegalStateException("Лимит использований этого промокода исчерпан");
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

