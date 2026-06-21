package com.arcx.ctfplatform.modifiers.repository;

import com.arcx.ctfplatform.modifiers.entity.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.Optional;
import java.util.List;

public interface PromoCodeRepository extends JpaRepository<PromoCode, UUID> {
    Optional<PromoCode> findByCode(String code);
}

