package com.arcx.ctfplatform.modifiers.repository;

import com.arcx.ctfplatform.modifiers.entity.ScoreAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface ScoreAdjustmentRepository extends JpaRepository<ScoreAdjustment, UUID> {
    List<ScoreAdjustment> findAllByStudentId(UUID studentId);
}
