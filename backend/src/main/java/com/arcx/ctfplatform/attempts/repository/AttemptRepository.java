package com.arcx.ctfplatform.attempts.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.arcx.ctfplatform.attempts.entity.Attempt;
import com.arcx.ctfplatform.attempts.dto.AttemptHistoryResponse;

@Repository
public interface AttemptRepository extends JpaRepository<Attempt, UUID> {
    boolean existsByTaskIdAndStudentIdAndIsCorrect(UUID taskId, UUID studentId, boolean isCorrect);
    long countByTaskIdAndIsCorrect(UUID taskId, boolean isCorrect);
    long countByTaskIdAndSubmittedAtBefore(UUID taskId, java.time.Instant submittedAt);

    java.util.List<Attempt> findAllByFilePathIsNotNullAndIsCorrectFalse();

    @org.springframework.data.jpa.repository.Query(
        "SELECT new com.arcx.ctfplatform.attempts.dto.AttemptHistoryResponse(" +
        "a.id, a.taskId, c.title, a.isCorrect, a.scoreAwarded, a.submittedAt) " +
        "FROM Attempt a JOIN CtfTask c ON a.taskId = c.id " +
        "WHERE a.studentId = :studentId " +
        "ORDER BY a.submittedAt DESC"
    )
    java.util.List<AttemptHistoryResponse> findHistoryByStudentId(
        @org.springframework.data.repository.query.Param("studentId") UUID studentId
    );

    @org.springframework.data.jpa.repository.Query(
        "SELECT new com.arcx.ctfplatform.attempts.dto.AttemptHistoryResponse(" +
        "a.id, a.taskId, c.title, a.isCorrect, a.scoreAwarded, a.submittedAt) " +
        "FROM Attempt a JOIN CtfTask c ON a.taskId = c.id " +
        "WHERE a.studentId = :studentId AND a.taskId = :taskId " +
        "ORDER BY a.submittedAt DESC"
    )
    java.util.List<AttemptHistoryResponse> findHistoryByStudentIdAndTaskId(
        @org.springframework.data.repository.query.Param("studentId") UUID studentId,
        @org.springframework.data.repository.query.Param("taskId") UUID taskId
    );
}
