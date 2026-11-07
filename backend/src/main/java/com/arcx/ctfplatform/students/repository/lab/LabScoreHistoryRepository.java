package com.arcx.ctfplatform.students.repository.lab;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.arcx.ctfplatform.students.entity.lab.LabScoreHistory;

@Repository
public interface LabScoreHistoryRepository extends JpaRepository<LabScoreHistory, UUID> {

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("DELETE FROM LabScoreHistory h")
    int deleteAllHistory();

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("DELETE FROM LabScoreHistory h WHERE h.student.id IN :studentIds")
    int deleteByStudentIdIn(@org.springframework.data.repository.query.Param("studentIds") java.util.Collection<UUID> studentIds);
}
