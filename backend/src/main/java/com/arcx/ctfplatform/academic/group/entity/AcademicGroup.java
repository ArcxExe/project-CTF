
package com.arcx.ctfplatform.academic.group.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@Table(name = "academic_groups")
public class AcademicGroup {

  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false, unique = true)
  private String name;

  @Column(name = "stream_id")
  private UUID streamId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;


  @PrePersist
  protected void onCreate() {
    this.createdAt = Instant.now();
  }

}
