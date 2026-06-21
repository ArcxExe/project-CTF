package com.arcx.ctfplatform.attempts.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.arcx.ctfplatform.attempts.entity.Attempt;
import com.arcx.ctfplatform.attempts.dto.AttemptHistoryResponse;

@Repository
public interface AttemptRepository extends JpaRepository<Attempt, UUID> {
    boolean existsByChallengeIdAndStudentIdAndIsCorrect(UUID challengeId, UUID studentId, boolean isCorrect);
    long countByChallengeIdAndIsCorrect(UUID challengeId, boolean isCorrect);
    long countByChallengeIdAndSubmittedAtBefore(UUID challengeId, java.time.Instant submittedAt);

    java.util.List<Attempt> findAllByFilePathIsNotNullAndIsCorrectFalse();

    @org.springframework.data.jpa.repository.Query(
        "SELECT new com.arcx.ctfplatform.attempts.dto.AttemptHistoryResponse(" +
        "a.id, a.challengeId, c.title, a.isCorrect, a.scoreAwarded, a.submittedAt) " +
        "FROM Attempt a JOIN Challenge c ON a.challengeId = c.id " +
        "WHERE a.studentId = :studentId " +
        "ORDER BY a.submittedAt DESC"
    )
    java.util.List<AttemptHistoryResponse> findHistoryByStudentId(
        @org.springframework.data.repository.query.Param("studentId") UUID studentId
    );

    @org.springframework.data.jpa.repository.Query(
        "SELECT new com.arcx.ctfplatform.attempts.dto.AttemptHistoryResponse(" +
        "a.id, a.challengeId, c.title, a.isCorrect, a.scoreAwarded, a.submittedAt) " +
        "FROM Attempt a JOIN Challenge c ON a.challengeId = c.id " +
        "WHERE a.studentId = :studentId AND a.challengeId = :challengeId " +
        "ORDER BY a.submittedAt DESC"
    )
    java.util.List<AttemptHistoryResponse> findHistoryByStudentIdAndChallengeId(
        @org.springframework.data.repository.query.Param("studentId") UUID studentId,
        @org.springframework.data.repository.query.Param("challengeId") UUID challengeId
    );
}
