package com.arcx.ctfplatform.tests.repository;

import com.arcx.ctfplatform.tests.entity.QuizAttempt;
import com.arcx.ctfplatform.tests.entity.QuizAttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {
    Optional<QuizAttempt> findByQuizIdAndStudentIdAndStatus(UUID quizId, UUID studentId, QuizAttemptStatus status);
    List<QuizAttempt> findAllByQuizIdAndStudentId(UUID quizId, UUID studentId);
    List<QuizAttempt> findAllByStatus(QuizAttemptStatus status);
}
