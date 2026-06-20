package com.arcx.ctfplatform.tests.repository;

import com.arcx.ctfplatform.tests.entity.QuizOption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface QuizOptionRepository extends JpaRepository<QuizOption, UUID> {
    List<QuizOption> findAllByQuestionIdOrderBySequenceOrderAsc(UUID questionId);
    List<QuizOption> findAllByQuestionIdIn(List<UUID> questionIds);
}
