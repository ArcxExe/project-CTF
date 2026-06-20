package com.arcx.ctfplatform.tests.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "quiz_submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class QuizSubmission {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "test_id", nullable = false)
    private UUID testId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "score", nullable = false)
    @Builder.Default
    private Integer score = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;
}
