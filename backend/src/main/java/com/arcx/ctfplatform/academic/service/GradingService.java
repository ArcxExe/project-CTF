package com.arcx.ctfplatform.academic.service;

import com.arcx.ctfplatform.academic.entity.GradingScale;
import com.arcx.ctfplatform.academic.repository.GradingScaleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class GradingService {

    private static final double BOUNDARY_GAP_TOLERANCE = 1.0 + 1e-9;

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
     * Uses dynamic rules from gradingScaleRepository (flow-specific -> global) when non-empty,
     * falling back to normative defaults:
     * - S < 50.00% -> 2
     * - 50.00% <= S < 70.00% -> 3
     * - 70.00% <= S < 85.00% -> 4
     * - S >= 85.00% -> 5 only if L >= 60.00% and K >= 60.00%, otherwise 4
     */
    public Integer calculateGrade(double s, double l, double k) {
        return calculateGrade(s, l, k, (UUID) null);
    }

    public Integer calculateGrade(double s, double l, double k, UUID flowId) {
        List<GradingScale> applicable = resolveApplicableScales(flowId);
        if (!applicable.isEmpty()) {
            boolean isFractionalScale = applicable.stream()
                    .allMatch(sc -> sc.getMaxCoefficient().doubleValue() <= 1.0
                            && sc.getMinCoefficient().doubleValue() >= 0.0);
            double factor = isFractionalScale ? 100.0 : 1.0;

            List<GradingScale> sortedAsc = applicable.stream()
                    .sorted(Comparator
                            .comparingDouble((GradingScale sc) -> sc.getMinCoefficient().doubleValue())
                            .thenComparingDouble(sc -> sc.getMaxCoefficient().doubleValue()))
                    .toList();

            int n = sortedAsc.size();
            for (int i = n - 1; i >= 0; i--) {
                GradingScale scale = sortedAsc.get(i);
                double min = scale.getMinCoefficient().doubleValue() * factor;
                double max = scale.getMaxCoefficient().doubleValue() * factor;
                boolean matches;
                if (i < n - 1) {
                    double nextMin = sortedAsc.get(i + 1).getMinCoefficient().doubleValue() * factor;
                    if (max <= nextMin && (nextMin - max) <= BOUNDARY_GAP_TOLERANCE) {
                        matches = s >= min && s < nextMin;
                    } else {
                        matches = s >= min && s <= max;
                    }
                } else {
                    matches = s >= min && s <= max;
                }

                if (matches) {
                    int g = scale.getGrade();
                    if (g == 5 && (l < 60.0 || k < 60.0)) {
                        return 4;
                    }
                    return g;
                }
            }

            double lowestMin = sortedAsc.get(0).getMinCoefficient().doubleValue() * factor;
            if (s < lowestMin) {
                return 2;
            }
        }

        return calculateDefaultGrade(s, l, k);
    }

    private List<GradingScale> resolveApplicableScales(UUID flowId) {
        if (gradingScaleRepository == null) {
            return List.of();
        }
        List<GradingScale> all = gradingScaleRepository.findAll();
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        List<GradingScale> valid = all.stream()
                .filter(sc -> sc != null
                        && sc.getMinCoefficient() != null
                        && sc.getMaxCoefficient() != null
                        && sc.getGrade() != null)
                .toList();
        if (valid.isEmpty()) {
            return List.of();
        }
        if (flowId != null) {
            List<GradingScale> flowScales = valid.stream()
                    .filter(sc -> flowId.equals(sc.getFlowId()))
                    .toList();
            if (!flowScales.isEmpty()) {
                return flowScales;
            }
        }
        return valid.stream()
                .filter(sc -> sc.getFlowId() == null)
                .toList();
    }

    private int calculateDefaultGrade(double s, double l, double k) {
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
        return calculateGrade(s, l, k, isEligible, null);
    }

    public Integer calculateGrade(double s, double l, double k, boolean isEligible, UUID flowId) {
        if (!isEligible) {
            return null;
        }
        return calculateGrade(s, l, k, flowId);
    }

    /**
     * Maps an exam score strictly on the 0..100 scale to a 2..5 academic grade.
     */
    public Integer mapExamScoreToGrade(Integer examScore) {
        if (examScore == null) {
            return null;
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
        return calculateFinalGradeWithExam(s, l, k, isEligible, examScore, null);
    }

    public Integer calculateFinalGradeWithExam(
            double s,
            double l,
            double k,
            boolean isEligible,
            Integer examScore,
            UUID flowId
    ) {
        if (!isEligible) {
            return null;
        }
        int courseGrade = calculateGrade(s, l, k, flowId);
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
        return getRecommendedGrade(sCoefficient, null);
    }

    public Integer getRecommendedGrade(double sCoefficient, UUID flowId) {
        double sPercent = sCoefficient <= 1.0 ? sCoefficient * 100.0 : sCoefficient;
        return calculateGrade(sPercent, 100.0, 100.0, flowId);
    }
}
