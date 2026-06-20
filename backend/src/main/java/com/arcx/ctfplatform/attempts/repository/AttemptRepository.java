package com.arcx.ctfplatform.attempts.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.arcx.ctfplatform.attempts.entity.Attempt;

@Repository
public interface AttemptRepository extends JpaRepository<Attempt, UUID> {
    boolean existsByChallengeIdAndStudentIdAndIsCorrect(UUID challengeId, UUID studentId, boolean isCorrect);
    long countByChallengeIdAndIsCorrect(UUID challengeId, boolean isCorrect);
    long countByChallengeIdAndSubmittedAtBefore(UUID challengeId, java.time.Instant submittedAt);
}
