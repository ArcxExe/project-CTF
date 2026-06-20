package com.arcx.ctfplatform.attempts.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Attempt {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "challenge_id", nullable = false)
    private UUID challengeId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "submitted_flag", nullable = false)
    private String submittedFlag;

    @Column(name = "is_correct", nullable = false)
    private boolean isCorrect;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @PrePersist
    protected void onCreate() {
        this.submittedAt = Instant.now();
    }
}
