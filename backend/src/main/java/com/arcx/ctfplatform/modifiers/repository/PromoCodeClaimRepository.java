package com.arcx.ctfplatform.modifiers.repository;

import com.arcx.ctfplatform.modifiers.entity.PromoCodeClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PromoCodeClaimRepository extends JpaRepository<PromoCodeClaim, UUID> {
    List<PromoCodeClaim> findAllByStudentId(UUID studentId);
    boolean existsByPromoCodeIdAndStudentId(UUID promoCodeId, UUID studentId);
    List<PromoCodeClaim> findAllByPromoCodeId(UUID promoCodeId);
}
