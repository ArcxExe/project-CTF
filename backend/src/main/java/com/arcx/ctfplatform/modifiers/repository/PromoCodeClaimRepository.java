package com.arcx.ctfplatform.modifiers.repository;

import com.arcx.ctfplatform.modifiers.entity.PromoCodeClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PromoCodeClaimRepository extends JpaRepository<PromoCodeClaim, UUID> {

    @Query("SELECT pcc FROM PromoCodeClaim pcc JOIN FETCH pcc.promoCode WHERE pcc.studentId = :studentId")
    List<PromoCodeClaim> findAllByStudentIdWithPromoCode(@Param("studentId") UUID studentId);

    List<PromoCodeClaim> findAllByStudentId(UUID studentId);
    boolean existsByPromoCodeIdAndStudentId(UUID promoCodeId, UUID studentId);
    List<PromoCodeClaim> findAllByPromoCodeId(UUID promoCodeId);
}
