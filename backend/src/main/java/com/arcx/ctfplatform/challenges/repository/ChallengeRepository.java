package com.arcx.ctfplatform.challenges.repository;

import com.arcx.ctfplatform.challenges.entity.Challenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface ChallengeRepository extends JpaRepository<Challenge, UUID> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Challenge c WHERE c.id = :id")
    Optional<Challenge> findByIdWithLock(@Param("id") UUID id);
}
