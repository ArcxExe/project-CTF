package com.arcx.ctfplatform.tests.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.arcx.ctfplatform.tests.entity.Test;
import com.arcx.ctfplatform.tests.entity.TestStatus;

@Repository
public interface TestRepository extends JpaRepository<Test, UUID> {
    List<Test> findAllByStatus(TestStatus status);

    @Modifying
    @Query(value = "DELETE FROM quiz_attempts WHERE quiz_id = :testId", nativeQuery = true)
    void deleteQuizAttemptsByTestId(UUID testId);
}

