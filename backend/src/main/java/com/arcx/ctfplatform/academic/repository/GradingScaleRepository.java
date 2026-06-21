package com.arcx.ctfplatform.academic.repository;

import com.arcx.ctfplatform.academic.entity.GradingScale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GradingScaleRepository extends JpaRepository<GradingScale, UUID> {
}
