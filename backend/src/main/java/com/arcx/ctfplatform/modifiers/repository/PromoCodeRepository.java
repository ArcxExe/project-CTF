package com.arcx.ctfplatform.modifiers.repository;

import com.arcx.ctfplatform.modifiers.entity.PromoCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PromoCodeRepository extends JpaRepository<PromoCode, UUID> {
    Optional<PromoCode> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PromoCode p WHERE UPPER(p.code) = UPPER(:code)")
    Optional<PromoCode> findByCodeForUpdate(@Param("code") String code);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PromoCode p SET p.usedCount = 0")
    int resetAllUsedCounts();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PromoCode p SET p.usedCount = (SELECT COUNT(c) FROM PromoCodeClaim c WHERE c.promoCode.id = p.id)")
    int recalculateUsedCounts();
}

