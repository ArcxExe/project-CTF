package com.arcx.ctfplatform.modifiers.entity;

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
@Table(name = "promo_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class PromoCode {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "modifier_type", nullable = false, length = 50)
    private PromoModifierType modifierType;

    @Column(nullable = false)
    private Integer value;

    @Column(name = "is_used", nullable = false)
    @Builder.Default
    private boolean isUsed = false;

    @Column(name = "used_by_student_id")
    private UUID usedByStudentId;

    @Column(name = "used_at")
    private Instant usedAt;
}
