package com.arcx.ctfplatform.students.repository.lab;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.arcx.ctfplatform.students.entity.lab.LabScoreHistory;

@Repository
public interface LabScoreHistoryRepository extends JpaRepository<LabScoreHistory, UUID> {
}
