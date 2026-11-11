package com.arcx.ctfplatform.academic.service;

import com.arcx.ctfplatform.academic.entity.GradingScale;
import com.arcx.ctfplatform.academic.repository.GradingScaleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class GradingService {

    private GradingScaleRepository gradingScaleRepository;

    public GradingService() {
    }

    @Autowired
    public GradingService(@Autowired(required = false) GradingScaleRepository gradingScaleRepository) {
        this.gradingScaleRepository = gradingScaleRepository;
    }

    public void setGradingScaleRepository(GradingScaleRepository gradingScaleRepository) {
        this.gradingScaleRepository = gradingScaleRepository;
    }

    /**
     * Calculates the academic grade based on percentages (0.0 .. 100.0):
     * Uses dynamic rules from gradingScaleRepository when non-empty, falling back to:
     * - S < 50.00% -> 2
     * - 50.00% <= S < 70.00% -> 3
     * - 70.00% <= S < 85.00% -> 4
     * - S >= 85.00% -> 5 only if L >= 60.00% and K >= 60.00%, otherwise 4
     */
    public Integer calculateGrade(double s, double l, double k) {
        if (gradingScaleRepository != null) {
            List<GradingScale> scales = gradingScaleRepository.findAll();
            if (scales != null && !scales.isEmpty()) {
                List<GradingScale> sorted = scales.stream()
                        .filter(sc -> sc.getMinCoefficient() != null && sc.getMaxCoefficient() != null && sc.getGrade() != null)
                        .sorted(Comparator.comparing(GradingScale::getMinCoefficient).reversed())
                        .toList();
                for (GradingScale scale : sorted) {
                    double min = scale.getMinCoefficient().doubleValue();
                    double max = scale.getMaxCoefficient().doubleValue();
                    if (max <= 1.0 && (s > 1.0 || l > 1.0 || k > 1.0)) {
                        min *= 100.0;
                        max *= 100.0;
                    }
                    if (s >= min && s <= max) {
                        int g = scale.getGrade();
                        if (g == 5 && (l < 60.0 || k < 60.0)) {
                            return 4;
                        }
                        return g;
                    }
                }
            }
        }

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

    public Integer mapExamScoreToGrade(Integer examScore) {
        if (examScore == null) {
            return null;
        }
        if (examScore >= 2 && examScore <= 5) {
            return examScore;
        }
        if (examScore < 50) {
            return 2;
        } else if (examScore < 70) {
            return 3;
        } else if (examScore < 85) {
            return 4;
        } else {
            return 5;
        }
    }

    public Integer calculateFinalGradeWithExam(double s, double l, double k, boolean isEligible, Integer examScore) {
        if (!isEligible) {
            return null;
        }
        int courseGrade = calculateGrade(s, l, k);
        if (examScore != null) {
            int examGrade = mapExamScoreToGrade(examScore);
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
        if (gradingScaleRepository != null) {
            BigDecimal sCoeffDecimal = BigDecimal.valueOf(sCoefficient);
            BigDecimal sPercentDecimal = BigDecimal.valueOf(sCoefficient * 100.0);
            List<GradingScale> scales = gradingScaleRepository.findAll();
            if (scales != null) {
                for (GradingScale scale : scales) {
                    if (scale.getMinCoefficient() == null || scale.getMaxCoefficient() == null || scale.getGrade() == null) {
                        continue;
                    }
                    BigDecimal compareValue = scale.getMaxCoefficient().compareTo(BigDecimal.ONE) > 0
                            ? sPercentDecimal
                            : sCoeffDecimal;
                    if (compareValue.compareTo(scale.getMinCoefficient()) >= 0 &&
                        compareValue.compareTo(scale.getMaxCoefficient()) <= 0) {
                        return scale.getGrade();
                    }
                }
            }
        }
        return calculateGrade(sCoefficient * 100.0, 100.0, 100.0);
    }
}
