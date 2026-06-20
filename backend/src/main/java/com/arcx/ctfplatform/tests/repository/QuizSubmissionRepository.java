package com.arcx.ctfplatform.tests.repository;

import com.arcx.ctfplatform.tests.entity.QuizSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface QuizSubmissionRepository extends JpaRepository<QuizSubmission, UUID> {
    Optional<QuizSubmission> findByTestIdAndStudentIdAndIsActiveTrue(UUID testId, UUID studentId);
    List<QuizSubmission> findAllByTestIdAndStudentId(UUID testId, UUID studentId);
}
