package com.arcx.ctfplatform.tests.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.arcx.ctfplatform.challenges.entity.Challenge;

@Entity
@Table(name = "tests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Test {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "competition_id")
    private UUID competitionId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TestStatus status;

    @Column(name = "time_limit_minutes", nullable = false)
    @Builder.Default
    private Integer timeLimitMinutes = 60;

    @Column(name = "passing_score", nullable = false)
    @Builder.Default
    private Integer passingScore = 50;

    @Column(name = "questions_count", nullable = false)
    @Builder.Default
    private Integer questionsCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToMany
    @JoinTable(
        name = "test_challenges",
        joinColumns = @JoinColumn(name = "test_id"),
        inverseJoinColumns = @JoinColumn(name = "challenge_id")
    )
    @Builder.Default
    private List<Challenge> challenges = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        if (this.status == null) {
            this.status = TestStatus.DRAFT;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
