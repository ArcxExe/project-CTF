package com.arcx.ctfplatform.academic.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "students")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Student {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "first_name", nullable = false, length = 100)
    @Builder.Default
    private String firstName = "New";

    @Column(name = "last_name", nullable = false, length = 100)
    @Builder.Default
    private String lastName = "Student";

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private AcademicGroup academicGroup;

    public UUID getGroupId() {
        return academicGroup != null ? academicGroup.getId() : null;
    }

    @Column(name = "student_code", nullable = false, unique = true, length = 50)
    private String studentCode;

    @Column(name = "user_id", unique = true)
    private UUID userId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private StudentStatus status = StudentStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        if (this.status == null) {
            this.status = StudentStatus.ACTIVE;
        }
        if (this.firstName == null) {
            this.firstName = "New";
        }
        if (this.lastName == null) {
            this.lastName = "Student";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
