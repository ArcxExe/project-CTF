package com.arcx.ctfplatform.students.service.lab;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arcx.ctfplatform.students.dto.lab.LabScoreResponse;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.students.entity.lab.LabScore;
import com.arcx.ctfplatform.students.entity.lab.LabScoreHistory;
import com.arcx.ctfplatform.students.entity.lab.StudentLab;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.students.repository.lab.LabScoreHistoryRepository;
import com.arcx.ctfplatform.students.repository.lab.LabScoreRepository;
import com.arcx.ctfplatform.students.repository.lab.StudentLabRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LabScoreService {

    private final LabScoreRepository labScoreRepository;
    private final LabScoreHistoryRepository labScoreHistoryRepository;
    private final StudentLabRepository studentLabRepository;
    private final StudentRepository studentRepository;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private com.arcx.ctfplatform.leaderboard.service.LeaderboardService leaderboardService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.arcx.ctfplatform.users.repository.UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.arcx.ctfplatform.audit.service.AuditService auditService;

    private final ThreadLocal<Boolean> suppressBroadcast = new ThreadLocal<>();

    public void setSuppressBroadcast(boolean suppress) {
        if (suppress) {
            suppressBroadcast.set(Boolean.TRUE);
        } else {
            suppressBroadcast.remove();
        }
    }

    public void broadcastAfterBulkUpdate() {
        if (!Boolean.TRUE.equals(suppressBroadcast.get()) && leaderboardService != null) {
            leaderboardService.broadcastUpdate();
        }
    }

    @Transactional(readOnly = true)
    public UUID resolveStudentIdForAuth(org.springframework.security.core.Authentication auth) {
        if (auth == null) {
            return null;
        }
        if (auth.getPrincipal() instanceof com.arcx.ctfplatform.users.entity.User u && u.getId() != null) {
            return studentRepository.findByUserId(u.getId()).map(Student::getId).orElse(null);
        }
        String name = auth.getName();
        if (name != null && !name.isBlank() && userRepository != null) {
            return userRepository.findByUsername(name)
                    .or(() -> userRepository.findByEmail(name))
                    .flatMap(u -> studentRepository.findByUserId(u.getId()))
                    .map(Student::getId)
                    .orElse(null);
        }
        return null;
    }

    public record LabSpec(int labNumber, double maxRawScore, double maxDefenseScore) {
        public double maxTotal() {
            return maxRawScore + maxDefenseScore;
        }
    }

    public static final Map<Integer, LabSpec> LAB_SPECS = Map.of(
            1, new LabSpec(1, 8.0, 2.0),
            2, new LabSpec(2, 10.0, 2.0),
            3, new LabSpec(3, 10.0, 2.0),
            4, new LabSpec(4, 12.0, 3.0),
            5, new LabSpec(5, 20.0, 4.0),
            6, new LabSpec(6, 24.0, 4.0),
            7, new LabSpec(7, 16.0, 4.0),
            8, new LabSpec(8, 18.0, 4.0)
    );

    public static LabSpec getLabSpec(int labNumber) {
        LabSpec spec = LAB_SPECS.get(labNumber);
        if (spec == null) {
            throw new IllegalArgumentException("Invalid lab number: " + labNumber + ". Expected 1 to 8.");
        }
        return spec;
    }

    public static double roundToTwoDecimals(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Returns total score for student's 8 laboratory works, accounting for late penalties.
     */
    @Transactional(readOnly = true)
    public double getFinalLabScore(UUID studentId) {
        List<StudentLab> labs = studentLabRepository.findAllByStudentId(studentId);
        if (!labs.isEmpty()) {
            double sum = labs.stream().mapToDouble(StudentLab::getScore).sum();
            return roundToTwoDecimals(sum);
        }
        return labScoreRepository.findByStudentId(studentId)
                .map(s -> roundToTwoDecimals((double) s.getScore()))
                .orElse(0.0);
    }

    @Transactional(readOnly = true)
    public double calculateTotalLabScore(UUID studentId) {
        return getFinalLabScore(studentId);
    }

    @Transactional(readOnly = true)
    public int getLabScore(UUID studentId) {
        return (int) Math.round(getFinalLabScore(studentId));
    }

    /**
     * Checks if all 8 laboratory works are accepted.
     * Criteria for acceptance of each lab:
     * - >= 40% raw score (raw_score >= 0.40 * max_raw_score)
     * - >= 50% individual defense score (defense_score >= 0.50 * max_defense_score)
     */
    @Transactional(readOnly = true)
    public boolean isEligible(UUID studentId) {
        List<StudentLab> labs = studentLabRepository.findAllByStudentId(studentId);
        if (!labs.isEmpty()) {
            Set<Integer> acceptedLabNumbers = labs.stream()
                    .filter(StudentLab::isAccepted)
                    .map(StudentLab::getLabNumber)
                    .collect(Collectors.toSet());
            for (int i = 1; i <= 8; i++) {
                if (!acceptedLabNumbers.contains(i)) {
                    return false;
                }
            }
            return true;
        }

        Optional<LabScore> labScoreOpt = labScoreRepository.findByStudentId(studentId);
        return labScoreOpt.map(LabScore::isEligible).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean checkEligibility(UUID studentId) {
        return isEligible(studentId);
    }

    @Transactional(readOnly = true)
    public boolean isAdmitted(UUID studentId) {
        return isEligible(studentId);
    }

    /**
     * Records or updates a laboratory work score and computes acceptance status.
     */
    @Transactional
    public void recordLab(UUID studentId, int labNumber, double rawScore, double maxRawScore,
                          double defenseScore, double maxDefenseScore, double penaltyCoefficient) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        if (maxRawScore <= 0 && maxDefenseScore <= 0 && LAB_SPECS.containsKey(labNumber)) {
            LabSpec spec = LAB_SPECS.get(labNumber);
            maxRawScore = spec.maxRawScore();
            maxDefenseScore = spec.maxDefenseScore();
        }

        maxRawScore = Math.max(0.0, maxRawScore);
        maxDefenseScore = Math.max(0.0, maxDefenseScore);
        rawScore = Math.max(0.0, maxRawScore > 0 ? Math.min(rawScore, maxRawScore) : rawScore);
        defenseScore = Math.max(0.0, maxDefenseScore > 0 ? Math.min(defenseScore, maxDefenseScore) : defenseScore);
        penaltyCoefficient = Math.max(0.0, Math.min(penaltyCoefficient, 1.0));

        double maxTotal = maxRawScore + maxDefenseScore;
        double totalRaw = rawScore + defenseScore;
        double calculatedScore = totalRaw * penaltyCoefficient;
        if (maxTotal > 0) {
            calculatedScore = Math.max(0.0, Math.min(calculatedScore, maxTotal));
        }

        boolean defensePassed = (maxDefenseScore <= 0) || (defenseScore >= 0.50 * maxDefenseScore);
        if (!defensePassed) {
            calculatedScore = Math.min(calculatedScore, 0.60 * maxTotal);
        }

        calculatedScore = roundToTwoDecimals(calculatedScore);

        boolean rawPassed = (maxRawScore <= 0 || rawScore >= 0.40 * maxRawScore) &&
                            (maxTotal <= 0 || totalRaw >= 0.40 * maxTotal);
        boolean accepted = rawPassed && defensePassed;

        StudentLab lab = studentLabRepository.findByStudentIdAndLabNumber(studentId, labNumber)
                .orElseGet(() -> StudentLab.builder().student(student).labNumber(labNumber).build());

        lab.setRawScore(rawScore);
        lab.setMaxRawScore(maxRawScore);
        lab.setDefenseScore(defenseScore);
        lab.setMaxDefenseScore(maxDefenseScore);
        lab.setPenaltyCoefficient(penaltyCoefficient);
        lab.setAccepted(accepted);
        lab.setScore(calculatedScore);

        studentLabRepository.save(lab);
        updateAggregatedLabScore(student);
        broadcastAfterBulkUpdate();
    }

    @Transactional
    public void recordLab(UUID studentId, int labNumber, double rawScore, double defenseScore, double penaltyCoefficient) {
        LabSpec spec = getLabSpec(labNumber);
        recordLab(studentId, labNumber, rawScore, spec.maxRawScore(), defenseScore, spec.maxDefenseScore(), penaltyCoefficient);
    }

    @Transactional
    public void recordLab(UUID studentId, int labNumber, double score, boolean accepted) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        StudentLab lab = studentLabRepository.findByStudentIdAndLabNumber(studentId, labNumber)
                .orElseGet(() -> StudentLab.builder().student(student).labNumber(labNumber).build());

        if (lab.getMaxRawScore() <= 0 && lab.getMaxDefenseScore() <= 0 && LAB_SPECS.containsKey(labNumber)) {
            LabSpec spec = LAB_SPECS.get(labNumber);
            lab.setMaxRawScore(spec.maxRawScore());
            lab.setMaxDefenseScore(spec.maxDefenseScore());
        }
        double clampedScore = Math.max(0.0, score);
        if (LAB_SPECS.containsKey(labNumber)) {
            clampedScore = Math.min(clampedScore, LAB_SPECS.get(labNumber).maxTotal());
        }
        lab.setScore(roundToTwoDecimals(clampedScore));
        lab.setAccepted(accepted);
        studentLabRepository.save(lab);
        updateAggregatedLabScore(student);
        broadcastAfterBulkUpdate();
    }

    @Transactional
    public void setEligibility(UUID studentId, boolean isEligible) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        List<StudentLab> existingLabs = studentLabRepository.findAllByStudentId(studentId);
        if (existingLabs != null && !existingLabs.isEmpty()) {
            Map<Integer, StudentLab> byNum = existingLabs.stream()
                    .collect(Collectors.toMap(StudentLab::getLabNumber, l -> l, (a, b) -> b));
            for (int i = 1; i <= 8; i++) {
                LabSpec spec = LAB_SPECS.get(i);
                StudentLab sl = byNum.get(i);
                if (sl == null && isEligible) {
                    sl = StudentLab.builder()
                            .student(student)
                            .labNumber(i)
                            .maxRawScore(spec.maxRawScore())
                            .maxDefenseScore(spec.maxDefenseScore())
                            .isAccepted(true)
                            .score(0.0)
                            .build();
                } else if (sl != null) {
                    sl.setAccepted(isEligible);
                }
                if (sl != null) {
                    studentLabRepository.save(sl);
                }
            }
        }
        LabScore labScore = labScoreRepository.findByStudentId(studentId)
                .orElseGet(() -> LabScore.builder().student(student).build());
        labScore.setEligible(isEligible);
        labScoreRepository.save(labScore);
        broadcastAfterBulkUpdate();
    }

    public void updateAggregatedLabScore(Student student) {
        List<StudentLab> labs = studentLabRepository.findAllByStudentId(student.getId());
        double totalScore = labs.stream().mapToDouble(StudentLab::getScore).sum();
        totalScore = roundToTwoDecimals(totalScore);
        boolean eligible = isEligible(student.getId());

        LabScore labScore = labScoreRepository.findByStudentId(student.getId())
                .orElseGet(() -> LabScore.builder().student(student).build());
        labScore.setScore((int) Math.round(totalScore));
        labScore.setEligible(eligible);
        labScoreRepository.save(labScore);
    }

    @Transactional
    public void setScore(UUID studentId, int score, String reason, UUID adminUserId) {
        setScore(studentId, score, true, reason, adminUserId);
    }

    @Transactional
    public void setScore(UUID studentId, int score, boolean isEligible, String reason, UUID adminUserId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        int clampedScore = Math.max(0, Math.min(score, 143));

        Optional<LabScore> existingScoreOpt = labScoreRepository.findByStudentId(studentId);
        int oldScore = existingScoreOpt.map(LabScore::getScore).orElse(0);

        List<StudentLab> existingLabs = studentLabRepository.findAllByStudentId(studentId);
        if (existingLabs != null && !existingLabs.isEmpty()) {
            Map<Integer, StudentLab> byNum = existingLabs.stream()
                    .collect(Collectors.toMap(StudentLab::getLabNumber, l -> l, (a, b) -> b));
            double ratio = clampedScore / 143.0;
            double runningSum = 0.0;
            for (int i = 1; i <= 8; i++) {
                LabSpec spec = LAB_SPECS.get(i);
                StudentLab sl = byNum.getOrDefault(i, StudentLab.builder()
                        .student(student)
                        .labNumber(i)
                        .maxRawScore(spec.maxRawScore())
                        .maxDefenseScore(spec.maxDefenseScore())
                        .penaltyCoefficient(1.0)
                        .build());
                double labScoreVal = (i < 8)
                        ? roundToTwoDecimals(spec.maxTotal() * ratio)
                        : roundToTwoDecimals(Math.max(0.0, clampedScore - runningSum));
                runningSum = roundToTwoDecimals(runningSum + labScoreVal);
                sl.setMaxRawScore(spec.maxRawScore());
                sl.setMaxDefenseScore(spec.maxDefenseScore());
                sl.setRawScore(roundToTwoDecimals(spec.maxRawScore() * ratio));
                sl.setDefenseScore(roundToTwoDecimals(Math.max(0.0, labScoreVal - sl.getRawScore())));
                sl.setScore(labScoreVal);
                sl.setAccepted(isEligible);
                studentLabRepository.save(sl);
            }
        }

        LabScore labScore = existingScoreOpt.orElseGet(() -> LabScore.builder().student(student).build());
        labScore.setScore(clampedScore);
        labScore.setEligible(isEligible);

        labScoreRepository.save(labScore);

        LabScoreHistory history = LabScoreHistory.builder()
                .student(student)
                .oldScore(oldScore)
                .newScore(clampedScore)
                .changedByUserId(adminUserId)
                .reason(reason != null && !reason.isBlank() ? reason : "Manual update")
                .build();
        labScoreHistoryRepository.save(history);
        if (auditService != null) {
            auditService.logEvent(
                    adminUserId,
                    "ADMIN",
                    "ADMIN",
                    "LAB_SCORE_UPDATE",
                    "STUDENT:" + studentId,
                    String.valueOf(oldScore),
                    String.valueOf(clampedScore),
                    null
            );
        }
        broadcastAfterBulkUpdate();
    }

    @Transactional
    public void importLabScores(InputStream csvStream, UUID adminUserId) {
        setSuppressBroadcast(true);
        boolean anyUpdated = false;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(csvStream, java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    if (line.startsWith("\uFEFF")) {
                        line = line.substring(1);
                    }
                    String[] headerParts = line.split("[,;]");
                    if (headerParts.length < 2) {
                        continue;
                    }
                    try {
                        Integer.parseInt(headerParts[1].trim());
                    } catch (NumberFormatException ignored) {
                        continue; // Skip header row
                    }
                }
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] parts = line.split("[,;]");
                if (parts.length < 2) {
                    throw new IllegalArgumentException("Invalid CSV row: expected studentCode and score");
                }
                String studentCode = parts[0].replace("\uFEFF", "").trim();
                int score;
                try {
                    score = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid score in CSV for student '" + studentCode + "': " + parts[1], e);
                }

                if (score < 0) {
                    continue;
                }

                Optional<Student> studentOpt = studentRepository.findByStudentCode(studentCode);
                if (studentOpt.isPresent()) {
                    Student student = studentOpt.get();
                    setScore(student.getId(), score, "CSV Import", adminUserId);
                    anyUpdated = true;
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to import CSV: " + e.getMessage(), e);
        } finally {
            setSuppressBroadcast(false);
            if (anyUpdated) {
                broadcastAfterBulkUpdate();
            }
        }
    }

    @Transactional(readOnly = true)
    public double calculateV1(UUID studentId, String scopeType, UUID scopeId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        double exactScore = getFinalLabScore(student.getId());
        if (exactScore <= 0.0) {
            return 0.0;
        }
        return roundToTwoDecimals(Math.min(Math.max(exactScore / 143.0, 0.0), 1.0));
    }

    private int getMMax(String scopeType, UUID scopeId) {
        return switch (scopeType.toUpperCase()) {
            case "GROUP" -> labScoreRepository.findMaxScoreByGroupId(scopeId).orElse(0);
            case "FLOW" -> labScoreRepository.findMaxScoreByStreamId(scopeId).orElse(0);
            case "GLOBAL" -> labScoreRepository.findMaxScoreGlobal().orElse(0);
            default -> throw new IllegalArgumentException("Invalid scope type: " + scopeType);
        };
    }

    @Transactional(readOnly = true)
    public List<LabScoreResponse> getAllLabScores(String scopeType, UUID scopeId) {
        List<Student> allStudents = studentRepository.findAll();
        if (!allStudents.isEmpty()) {
            if (scopeType != null && scopeId != null) {
                if ("GROUP".equalsIgnoreCase(scopeType)) {
                    allStudents = allStudents.stream()
                            .filter(s -> scopeId.equals(s.getGroupId()))
                            .toList();
                } else if ("FLOW".equalsIgnoreCase(scopeType)) {
                    allStudents = allStudents.stream()
                            .filter(s -> s.getAcademicGroup() != null && scopeId.equals(s.getAcademicGroup().getStreamId()))
                            .toList();
                }
            }
            return allStudents.stream()
                    .map(student -> {
                        double exactScore = getFinalLabScore(student.getId());
                        int roundedScore = (int) Math.round(exactScore);
                        double v1 = roundToTwoDecimals(Math.min(Math.max(exactScore / 143.0, 0.0), 1.0));
                        String studentName = student.getFullName();
                        if (studentName == null || studentName.isBlank()) {
                            studentName = (student.getFirstName() != null ? student.getFirstName() : "") + " "
                                    + (student.getLastName() != null ? student.getLastName() : "");
                        }
                        String groupName = student.getAcademicGroup() != null ? student.getAcademicGroup().getName() : "";
                        String statusStr = student.getStatus() != null ? student.getStatus().name().toLowerCase() : "";

                        return LabScoreResponse.builder()
                                .studentId(student.getId())
                                .studentCode(student.getStudentCode())
                                .studentName(studentName.trim())
                                .groupName(groupName)
                                .status(statusStr)
                                .score(roundedScore)
                                .v1Coefficient(v1)
                                .build();
                    })
                    .collect(Collectors.toList());
        }

        List<LabScore> scores = labScoreRepository.findAll();
        return scores.stream()
                .map(score -> {
                    double v1 = roundToTwoDecimals(Math.min(Math.max(score.getScore() / 143.0, 0.0), 1.0));
                    String studentName = score.getStudent().getFullName();
                    if (studentName == null || studentName.isBlank()) {
                        studentName = score.getStudent().getFirstName() + " " + score.getStudent().getLastName();
                    }
                    String groupName = score.getStudent().getAcademicGroup() != null ? score.getStudent().getAcademicGroup().getName() : "";
                    String statusStr = score.getStudent().getStatus() != null ? score.getStudent().getStatus().name().toLowerCase() : "";

                    return LabScoreResponse.builder()
                            .studentId(score.getStudent().getId())
                            .studentCode(score.getStudent().getStudentCode())
                            .studentName(studentName)
                            .groupName(groupName)
                            .status(statusStr)
                            .score(score.getScore())
                            .v1Coefficient(v1)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
