package com.arcx.ctfplatform.challenges.entity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@Table(name = "challenges")
public class Challenge {

  @Id
  @GeneratedValue
  private UUID id;

  @Column(name = "competition_id")
  private UUID competitionId;

  @Column(nullable = false)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(nullable = false)
  private Integer points = 100;

  @Column(nullable = false)
  private String flag;

  @Column(name = "difficulty_level", length = 30)
  private String difficultyLevel;

  @Column(name = "max_score", nullable = false)
  private Integer maxScore = 100;

  @Column(name = "is_first_blood_only", nullable = false)
  private boolean isFirstBloodOnly = false;

  @Enumerated(EnumType.STRING)
  @Column(name = "task_type", nullable = false, length = 50)
  private TaskType taskType = TaskType.FLAG;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> attachments = new HashMap<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  protected void onCreate() {
    this.createdAt = Instant.now();
  }

}
