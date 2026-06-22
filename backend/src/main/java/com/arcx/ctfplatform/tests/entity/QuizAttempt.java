package com.arcx.ctfplatform.tests.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "quiz_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class QuizAttempt {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "quiz_id", nullable = false)
    private UUID quizId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "score", nullable = false)
    @Builder.Default
    private Integer score = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private QuizAttemptStatus status = QuizAttemptStatus.IN_PROGRESS;
}
