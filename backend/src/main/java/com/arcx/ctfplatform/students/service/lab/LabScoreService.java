package com.arcx.ctfplatform.students.service.lab;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
    public record StudentLabSummary(double finalScore, boolean isEligible) {
    }

    public record LabScoreImportReport(int updatedCount, int skippedCount, List<String> errors) {
    }

    @Transactional(readOnly = true)
    public double getFinalLabScore(UUID studentId) {
        List<StudentLab> labs = studentLabRepository.findAllByStudentId(studentId);
        LabScore labScore = labScoreRepository.findByStudentId(studentId).orElse(null);
        return computeFinalLabScore(labs, labScore);
    }

    @Transactional(readOnly = true)
    public Map<UUID, StudentLabSummary> getLabSummariesForStudents(Collection<UUID> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<StudentLab> allLabs = studentLabRepository.findAllByStudentIdIn(studentIds);
        List<LabScore> allLabScores = labScoreRepository.findAllByStudentIdIn(studentIds);

        Map<UUID, List<StudentLab>> labsByStudent = new HashMap<>();
        if (allLabs != null) {
            for (StudentLab lab : allLabs) {
                if (lab != null && lab.getStudent() != null && lab.getStudent().getId() != null) {
                    labsByStudent.computeIfAbsent(lab.getStudent().getId(), k -> new ArrayList<>()).add(lab);
                }
            }
        }

        Map<UUID, LabScore> labScoreByStudent = new HashMap<>();
        if (allLabScores != null) {
            for (LabScore ls : allLabScores) {
                if (ls != null && ls.getStudent() != null && ls.getStudent().getId() != null) {
                    labScoreByStudent.putIfAbsent(ls.getStudent().getId(), ls);
                }
            }
        }

        Map<UUID, StudentLabSummary> result = new HashMap<>(studentIds.size());
        for (UUID sid : studentIds) {
            if (sid == null) {
                continue;
            }
            List<StudentLab> labs = labsByStudent.getOrDefault(sid, Collections.emptyList());
            LabScore ls = labScoreByStudent.get(sid);
            result.put(sid, new StudentLabSummary(computeFinalLabScore(labs, ls), computeEligibility(labs, ls)));
        }
        return result;
    }

    private double computeFinalLabScore(List<StudentLab> labs, LabScore labScore) {
        if (labs != null && !labs.isEmpty()) {
            double sum = roundToTwoDecimals(labs.stream().mapToDouble(StudentLab::getScore).sum());
            if (labScore != null && (labScore.isManualScoreOverride() || labScore.getScore() != (int) Math.round(sum))) {
                return roundToTwoDecimals((double) labScore.getScore());
            }
            return sum;
        }
        return labScore != null
                ? roundToTwoDecimals((double) labScore.getScore())
                : 0.0;
    }

    private boolean computeEligibility(List<StudentLab> labs, LabScore labScore) {
        if (labScore != null && labScore.isManualEligibilityOverride()) {
            return labScore.isEligible();
        }
        if (labs != null && !labs.isEmpty()) {
            return areAllEightLabsAccepted(labs) || (labScore != null && labScore.isEligible());
        }
        return labScore != null && labScore.isEligible();
    }

    private boolean areAllEightLabsAccepted(List<StudentLab> labs) {
        if (labs == null || labs.isEmpty()) {
            return false;
        }
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
        LabScore labScore = labScoreRepository.findByStudentId(studentId).orElse(null);
        return computeEligibility(labs, labScore);
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
        LabSpec spec = getLabSpec(labNumber);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        if (maxRawScore <= 0 && maxDefenseScore <= 0) {
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
        LabSpec spec = getLabSpec(labNumber);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        StudentLab lab = studentLabRepository.findByStudentIdAndLabNumber(studentId, labNumber)
                .orElseGet(() -> StudentLab.builder().student(student).labNumber(labNumber).build());

        if (lab.getMaxRawScore() <= 0 && lab.getMaxDefenseScore() <= 0) {
            lab.setMaxRawScore(spec.maxRawScore());
            lab.setMaxDefenseScore(spec.maxDefenseScore());
        }
        double clampedScore = Math.max(0.0, Math.min(score, spec.maxTotal()));
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
        labScore.setManualEligibilityOverride(true);
        labScoreRepository.save(labScore);
        broadcastAfterBulkUpdate();
    }

    public void updateAggregatedLabScore(Student student) {
        List<StudentLab> labs = studentLabRepository.findAllByStudentId(student.getId());
        double totalScore = labs.stream().mapToDouble(StudentLab::getScore).sum();
        totalScore = roundToTwoDecimals(totalScore);

        LabScore labScore = labScoreRepository.findByStudentId(student.getId())
                .orElseGet(() -> LabScore.builder().student(student).build());
        labScore.setScore((int) Math.round(totalScore));
        labScore.setManualScoreOverride(false);
        if (!labScore.isManualEligibilityOverride()) {
            labScore.setEligible(areAllEightLabsAccepted(labs));
        }
        labScoreRepository.save(labScore);
    }

    @Transactional
    public void setScore(UUID studentId, int score, String reason, UUID adminUserId) {
        Optional<LabScore> existingOpt = labScoreRepository.findByStudentId(studentId);
        boolean eligible;
        boolean manualEligibilityOverride;
        if (existingOpt.isPresent()) {
            LabScore existing = existingOpt.get();
            eligible = existing.isEligible();
            manualEligibilityOverride = existing.isManualEligibilityOverride();
        } else {
            List<StudentLab> existingLabs = studentLabRepository.findAllByStudentId(studentId);
            eligible = areAllEightLabsAccepted(existingLabs);
            manualEligibilityOverride = false;
        }
        saveManualScore(studentId, score, eligible, manualEligibilityOverride, reason, adminUserId);
    }

    @Transactional
    public void setScore(UUID studentId, int score, boolean isEligible, String reason, UUID adminUserId) {
        saveManualScore(studentId, score, isEligible, true, reason, adminUserId);
    }

    private void saveManualScore(UUID studentId, int score, boolean isEligible,
                                 boolean manualEligibilityOverride, String reason, UUID adminUserId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        int clampedScore = Math.max(0, Math.min(score, 143));

        Optional<LabScore> existingScoreOpt = labScoreRepository.findByStudentId(studentId);
        int oldScore = existingScoreOpt.map(LabScore::getScore).orElse(0);

        LabScore labScore = existingScoreOpt.orElseGet(() -> LabScore.builder().student(student).build());
        labScore.setScore(clampedScore);
        labScore.setManualScoreOverride(true);
        labScore.setEligible(isEligible);
        labScore.setManualEligibilityOverride(manualEligibilityOverride);

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
            AuditActor actor = resolveAuditActor(adminUserId);
            auditService.logEvent(
                    adminUserId,
                    actor.username(),
                    actor.role(),
                    "LAB_SCORE_UPDATE",
                    "STUDENT:" + studentId,
                    String.valueOf(oldScore),
                    String.valueOf(clampedScore),
                    null
            );
        }
        broadcastAfterBulkUpdate();
    }

    private record AuditActor(String username, String role) {
    }

    private AuditActor resolveAuditActor(UUID adminUserId) {
        if (adminUserId != null && userRepository != null) {
            Optional<com.arcx.ctfplatform.users.entity.User> userOpt = userRepository.findById(adminUserId);
            if (userOpt.isPresent()) {
                com.arcx.ctfplatform.users.entity.User actor = userOpt.get();
                return new AuditActor(
                        actor.getUsername(),
                        actor.getRole() != null ? actor.getRole().name() : null
                );
            }
        }
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.arcx.ctfplatform.users.entity.User principal) {
            return new AuditActor(
                    principal.getUsername(),
                    principal.getRole() != null ? principal.getRole().name() : null
            );
        }
        return new AuditActor("UNKNOWN", null);
    }

    @Transactional
    public LabScoreImportReport importLabScores(InputStream csvStream, UUID adminUserId) {
        setSuppressBroadcast(true);
        int updatedCount = 0;
        int skippedCount = 0;
        List<String> errors = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(csvStream, java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.split("[,;]", -1);
                if (lineNumber == 1 && parts.length >= 2 && parts[1].trim().chars().anyMatch(Character::isLetter)) {
                    continue; // Skip text header row
                }
                if (parts.length < 2) {
                    skippedCount++;
                    errors.add("Строка " + lineNumber + ": ожидаются шифр студента и баллы");
                    continue;
                }
                String studentCode = parts[0].replace("\uFEFF", "").trim();
                String rawScore = parts[1].trim();
                int score;
                try {
                    score = Integer.parseInt(rawScore);
                } catch (NumberFormatException e) {
                    skippedCount++;
                    errors.add("Строка " + lineNumber + ": некорректный балл '" + rawScore + "' для студента '" + studentCode + "'");
                    continue;
                }

                if (score < 0) {
                    skippedCount++;
                    errors.add("Строка " + lineNumber + ": отрицательный балл (" + score + ") для студента '" + studentCode + "'");
                    continue;
                }

                Optional<Student> studentOpt = studentRepository.findByStudentCode(studentCode);
                if (studentOpt.isEmpty()) {
                    skippedCount++;
                    errors.add("Строка " + lineNumber + ": студент '" + studentCode + "' не найден");
                    continue;
                }

                setScore(studentOpt.get().getId(), score, "CSV Import", adminUserId);
                updatedCount++;
            }
            return new LabScoreImportReport(updatedCount, skippedCount, errors);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to import CSV: " + e.getMessage(), e);
        } finally {
            setSuppressBroadcast(false);
            if (updatedCount > 0) {
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
    public Optional<LabScoreResponse> getLabScoreForStudent(UUID studentId, String scopeType, UUID scopeId) {
        if (studentId == null) {
            return Optional.empty();
        }
        Optional<Student> studentOpt = studentRepository.findById(studentId);
        if (studentOpt.isEmpty()) {
            return Optional.empty();
        }
        Student student = studentOpt.get();
        if (scopeType != null && scopeId != null) {
            if ("GROUP".equalsIgnoreCase(scopeType) && !scopeId.equals(student.getGroupId())) {
                return Optional.empty();
            }
            if ("FLOW".equalsIgnoreCase(scopeType)
                    && (student.getAcademicGroup() == null || !scopeId.equals(student.getAcademicGroup().getStreamId()))) {
                return Optional.empty();
            }
        }
        double exactScore = getFinalLabScore(student.getId());
        return Optional.of(toLabScoreResponse(student, exactScore));
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
            List<UUID> studentIds = allStudents.stream().map(Student::getId).toList();
            Map<UUID, StudentLabSummary> summaries = getLabSummariesForStudents(studentIds);
            return allStudents.stream()
                    .map(student -> {
                        StudentLabSummary summary = summaries.get(student.getId());
                        double exactScore = summary != null ? summary.finalScore() : getFinalLabScore(student.getId());
                        return toLabScoreResponse(student, exactScore);
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

    private LabScoreResponse toLabScoreResponse(Student student, double exactScore) {
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
    }
}
