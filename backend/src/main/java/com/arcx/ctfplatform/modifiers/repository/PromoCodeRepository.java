package com.arcx.ctfplatform.modifiers.repository;

import com.arcx.ctfplatform.modifiers.entity.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.Optional;
import java.util.List;

public interface PromoCodeRepository extends JpaRepository<PromoCode, UUID> {
    Optional<PromoCode> findByCode(String code);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("UPDATE PromoCode p SET p.usedCount = 0")
    int resetAllUsedCounts();

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("UPDATE PromoCode p SET p.usedCount = (SELECT COUNT(c) FROM PromoCodeClaim c WHERE c.promoCode.id = p.id)")
    int recalculateUsedCounts();
}

