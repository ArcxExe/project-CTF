package com.arcx.ctfplatform.modifiers.repository;

import com.arcx.ctfplatform.modifiers.entity.PromoCodeClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PromoCodeClaimRepository extends JpaRepository<PromoCodeClaim, UUID> {
    List<PromoCodeClaim> findAllByStudentId(UUID studentId);
    boolean existsByPromoCodeIdAndStudentId(UUID promoCodeId, UUID studentId);
    List<PromoCodeClaim> findAllByPromoCodeId(UUID promoCodeId);

    @Query("SELECT c FROM PromoCodeClaim c JOIN FETCH c.promoCode")
    List<PromoCodeClaim> findAllWithPromoCode();

    @Query("SELECT c FROM PromoCodeClaim c JOIN FETCH c.promoCode WHERE c.studentId = :studentId")
    List<PromoCodeClaim> findAllByStudentIdWithPromoCode(@org.springframework.data.repository.query.Param("studentId") UUID studentId);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PromoCodeClaim c")
    int deleteAllClaims();

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PromoCodeClaim c WHERE c.studentId IN :studentIds")
    int deleteByStudentIdIn(@org.springframework.data.repository.query.Param("studentIds") java.util.Collection<UUID> studentIds);
}
