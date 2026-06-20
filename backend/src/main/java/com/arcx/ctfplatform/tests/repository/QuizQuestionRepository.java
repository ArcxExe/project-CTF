package com.arcx.ctfplatform.tests.repository;

import com.arcx.ctfplatform.tests.entity.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {
    List<QuizQuestion> findAllByTestIdOrderByOrderingAsc(UUID testId);
}
