package com.arcx.ctfplatform.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "grading_scales")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradingScale {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "min_coefficient", nullable = false, precision = 3, scale = 2)
    private BigDecimal minCoefficient;

    @Column(name = "max_coefficient", nullable = false, precision = 3, scale = 2)
    private BigDecimal maxCoefficient;

    @Column(nullable = false)
    private Integer grade;

    @Column(length = 150)
    private String description;
}
