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
    List<QuizAttempt> findAllByStudentId(UUID studentId);
    List<QuizAttempt> findAllByStatus(QuizAttemptStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT qa FROM QuizAttempt qa, Test t WHERE qa.quizId = t.id AND t.type = :type")
    List<QuizAttempt> findAllByTestType(@org.springframework.data.repository.query.Param("type") com.arcx.ctfplatform.tests.entity.TestType type);

    @org.springframework.data.jpa.repository.Query("SELECT qa FROM QuizAttempt qa, Test t WHERE qa.quizId = t.id AND qa.studentId = :studentId AND t.type = :type")
    List<QuizAttempt> findAllByStudentIdAndTestType(
            @org.springframework.data.repository.query.Param("studentId") UUID studentId,
            @org.springframework.data.repository.query.Param("type") com.arcx.ctfplatform.tests.entity.TestType type);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("DELETE FROM QuizAttempt qa")
    int deleteAllQuizAttempts();

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("DELETE FROM QuizAttempt qa WHERE qa.studentId IN :studentIds")
    int deleteByStudentIdIn(@org.springframework.data.repository.query.Param("studentIds") java.util.Collection<UUID> studentIds);
}
