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

import com.arcx.ctfplatform.challenges.entity.CtfTask;

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

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    @Builder.Default
    private TestType type = TestType.LECTURE;

    @Column(name = "kt_number")
    private Integer ktNumber;

    public boolean isControlPoint() {
        if (type != null) {
            return type.isControlPoint();
        }
        return title != null && (title.toUpperCase().contains("КТ") || title.toLowerCase().contains("контрольн"));
    }

    public Integer getEffectiveKtNumber() {
        if (ktNumber != null && ktNumber >= 1 && ktNumber <= 4) {
            return ktNumber;
        }
        if (title != null && isControlPoint()) {
            for (int i = 1; i <= 4; i++) {
                if (title.contains(String.valueOf(i))) {
                    return i;
                }
            }
        }
        return null;
    }

    @Column(name = "time_limit_minutes", nullable = false)
    @Builder.Default
    private java.lang.Integer timeLimitMinutes = 60;

    @Column(name = "passing_score", nullable = false)
    @Builder.Default
    private java.lang.Integer passingScore = 50;

    @Column(name = "questions_count", nullable = false)
    @Builder.Default
    private java.lang.Integer questionsCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToMany
    @JoinTable(
        name = "test_tasks",
        joinColumns = @JoinColumn(name = "test_id"),
        inverseJoinColumns = @JoinColumn(name = "task_id")
    )
    @Builder.Default
    private List<CtfTask> tasks = new ArrayList<>();

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
