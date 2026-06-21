package com.arcx.ctfplatform.academic.service;

import com.arcx.ctfplatform.academic.entity.GradingScale;
import com.arcx.ctfplatform.academic.repository.GradingScaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GradingService {

    private final GradingScaleRepository gradingScaleRepository;

    public Integer getRecommendedGrade(double sCoefficient) {
        BigDecimal sCoeffDecimal = BigDecimal.valueOf(sCoefficient);
        List<GradingScale> scales = gradingScaleRepository.findAll();

        for (GradingScale scale : scales) {
            if (sCoeffDecimal.compareTo(scale.getMinCoefficient()) >= 0 &&
                sCoeffDecimal.compareTo(scale.getMaxCoefficient()) <= 0) {
                return scale.getGrade();
            }
        }
        return 2; // Default
    }
}
