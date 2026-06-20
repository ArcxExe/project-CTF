package com.arcx.ctfplatform.modifiers.service;

import com.arcx.ctfplatform.modifiers.entity.PromoCode;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeRepository;
import com.arcx.ctfplatform.students.entity.Student;
import com.arcx.ctfplatform.students.repository.StudentRepository;
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
    private final StudentRepository studentRepository;
    private final LeaderboardService leaderboardService;

    @Transactional
    public PromoCode claimCode(UUID userId, String code) {
        Student student = studentRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        PromoCode promo = promoCodeRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Invalid promo code"));

        if (promo.isUsed()) {
            throw new IllegalStateException("Promo code already used");
        }

        promo.setUsed(true);
        promo.setUsedByStudentId(student.getId());
        promo.setUsedAt(Instant.now());

        PromoCode saved = promoCodeRepository.save(promo);
        leaderboardService.broadcastUpdate();
        return saved;
    }
}
