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

    /**
     * Calculates the academic grade based on percentages (0.0 .. 100.0):
     * - S < 50.00% -> 2
     * - 50.00% <= S < 70.00% -> 3
     * - 70.00% <= S < 85.00% -> 4
     * - S >= 85.00% -> 5 only if L >= 60.00% and K >= 60.00%, otherwise 4
     */
    public Integer calculateGrade(double s, double l, double k) {
        if (s < 50.0) {
            return 2;
        } else if (s < 70.0) {
            return 3;
        } else if (s < 85.0) {
            return 4;
        } else {
            if (l >= 60.0 && k >= 60.0) {
                return 5;
            } else {
                return 4;
            }
        }
    }

    public Integer calculateGrade(double s, double l, double k, boolean isEligible) {
        if (!isEligible) {
            return null;
        }
        return calculateGrade(s, l, k);
    }

    public Integer calculateFinalGradeWithExam(double s, double l, double k, boolean isEligible, Integer examScore) {
        if (!isEligible) {
            return null;
        }
        int courseGrade = calculateGrade(s, l, k);
        if (examScore != null) {
            int examGrade;
            if (examScore < 50) {
                examGrade = 2;
            } else if (examScore < 70) {
                examGrade = 3;
            } else if (examScore < 85) {
                examGrade = 4;
            } else {
                examGrade = 5;
            }
            return Math.max(courseGrade, examGrade);
        }
        return courseGrade;
    }

    public Integer getRecommendedGrade(double s, double l, double k) {
        return calculateGrade(s, l, k);
    }

    public Integer getRecommendedGrade(double s, double l, double k, boolean isEligible) {
        return calculateGrade(s, l, k, isEligible);
    }

    public Integer getRecommendedGrade(double sCoefficient) {
        if (sCoefficient > 1.0) {
            return calculateGrade(sCoefficient, 100.0, 100.0);
        }
        BigDecimal sCoeffDecimal = BigDecimal.valueOf(sCoefficient);
        List<GradingScale> scales = gradingScaleRepository.findAll();

        for (GradingScale scale : scales) {
            if (sCoeffDecimal.compareTo(scale.getMinCoefficient()) >= 0 &&
                sCoeffDecimal.compareTo(scale.getMaxCoefficient()) <= 0) {
                return scale.getGrade();
            }
        }
        return calculateGrade(sCoefficient * 100.0, 100.0, 100.0);
    }
}
