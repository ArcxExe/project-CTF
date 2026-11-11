package com.arcx.ctfplatform.modifiers.repository;

import com.arcx.ctfplatform.modifiers.entity.ScoreAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface ScoreAdjustmentRepository extends JpaRepository<ScoreAdjustment, UUID> {
    List<ScoreAdjustment> findAllByStudentId(UUID studentId);
    List<ScoreAdjustment> findAllByStudentIdOrderByCreatedAtDesc(UUID studentId);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("UPDATE ScoreAdjustment s SET s.competitionId = null WHERE s.competitionId = :competitionId")
    int unlinkByCompetitionId(@org.springframework.data.repository.query.Param("competitionId") UUID competitionId);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("DELETE FROM ScoreAdjustment s WHERE s.competitionId = :competitionId")
    int deleteByCompetitionId(@org.springframework.data.repository.query.Param("competitionId") UUID competitionId);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("DELETE FROM ScoreAdjustment s")
    int deleteAllAdjustments();

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("DELETE FROM ScoreAdjustment s WHERE s.studentId IN :studentIds")
    int deleteByStudentIdIn(@org.springframework.data.repository.query.Param("studentIds") java.util.Collection<UUID> studentIds);
}
