package com.arcx.ctfplatform.students.entity.lab;

import java.time.Instant;
import java.util.UUID;

import com.arcx.ctfplatform.academic.entity.Student;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "student_labs", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"student_id", "lab_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class StudentLab {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "lab_number", nullable = false)
    private int labNumber;

    @Column(name = "raw_score", nullable = false)
    @Builder.Default
    private double rawScore = 0.0;

    @Column(name = "max_raw_score", nullable = false)
    @Builder.Default
    private double maxRawScore = 0.0;

    @Column(name = "defense_score", nullable = false)
    @Builder.Default
    private double defenseScore = 0.0;

    @Column(name = "max_defense_score", nullable = false)
    @Builder.Default
    private double maxDefenseScore = 0.0;

    @Column(name = "penalty_coefficient", nullable = false)
    @Builder.Default
    private double penaltyCoefficient = 1.0;

    @Column(name = "is_accepted", nullable = false)
    @Builder.Default
    private boolean isAccepted = false;

    public boolean isAccepted() {
        if (this.isAccepted) {
            return true;
        }
        if (maxRawScore > 0 || maxDefenseScore > 0) {
            double maxTotal = maxRawScore + maxDefenseScore;
            double totalRaw = rawScore + defenseScore;
            boolean rawPassed = (maxRawScore <= 0 || rawScore >= 0.40 * maxRawScore) &&
                                (maxTotal <= 0 || totalRaw >= 0.40 * maxTotal);
            boolean defensePassed = (maxDefenseScore <= 0 || defenseScore >= 0.50 * maxDefenseScore);
            return rawPassed && defensePassed;
        }
        return false;
    }

    @Column(name = "score", nullable = false)
    @Builder.Default
    private double score = 0.0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
