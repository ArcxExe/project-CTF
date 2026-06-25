package com.arcx.ctfplatform.challenges.repository;

import com.arcx.ctfplatform.challenges.entity.CtfTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface CtfTaskRepository extends JpaRepository<CtfTask, UUID> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CtfTask c WHERE c.id = :id")
    Optional<CtfTask> findByIdWithLock(@Param("id") UUID id);
}
