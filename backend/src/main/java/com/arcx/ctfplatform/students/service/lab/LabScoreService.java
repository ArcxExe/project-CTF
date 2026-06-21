package com.arcx.ctfplatform.students.service.lab;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arcx.ctfplatform.students.dto.lab.LabScoreResponse;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.students.entity.lab.LabScore;
import com.arcx.ctfplatform.students.entity.lab.LabScoreHistory;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.students.repository.lab.LabScoreHistoryRepository;
import com.arcx.ctfplatform.students.repository.lab.LabScoreRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LabScoreService {

    private final LabScoreRepository labScoreRepository;
    private final LabScoreHistoryRepository labScoreHistoryRepository;
    private final StudentRepository studentRepository;

    @Transactional
    public void setScore(UUID studentId, int score, String reason, UUID adminUserId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        Optional<LabScore> existingScoreOpt = labScoreRepository.findByStudentId(studentId);
        int oldScore = existingScoreOpt.map(LabScore::getScore).orElse(0);

        LabScore labScore = existingScoreOpt.orElseGet(() -> LabScore.builder().student(student).build());
        labScore.setScore(score);

        labScoreRepository.save(labScore);

        LabScoreHistory history = LabScoreHistory.builder()
                .student(student)
                .oldScore(oldScore)
                .newScore(score)
                .changedByUserId(adminUserId)
                .reason(reason)
                .build();
        labScoreHistoryRepository.save(history);
    }

    @Transactional
    public void importLabScores(InputStream csvStream, UUID adminUserId) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvStream))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue; // Skip header
                }

                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    String studentCode = parts[0].trim();
                    int score = Integer.parseInt(parts[1].trim());

                    if (score < 0) {
                        continue;
                    }

                    Optional<Student> studentOpt = studentRepository.findByStudentCode(studentCode);
                    if (studentOpt.isPresent()) {
                        Student student = studentOpt.get();
                        setScore(student.getId(), score, "CSV Import", adminUserId);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to import CSV", e);
        }
    }

    @Transactional(readOnly = true)
    public double calculateV1(UUID studentId, String scopeType, UUID scopeId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        Optional<LabScore> labScoreOpt = labScoreRepository.findByStudentId(studentId);
        if (labScoreOpt.isEmpty() || labScoreOpt.get().getScore() == 0) {
            return 0.0;
        }

        int m = labScoreOpt.get().getScore();
        int mMax = getMMax(scopeType, scopeId);

        if (mMax == 0) {
            return 0.0;
        }

        double v1 = (double) m / mMax;
        return Math.min(Math.max(v1, 0.0), 1.0);
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
        List<LabScore> scores = labScoreRepository.findAll();
        int mMax = getMMax(scopeType, scopeId);

        return scores.stream()
                .map(score -> {
                    double v1 = 0.0;
                    if (mMax > 0 && score.getScore() > 0) {
                        v1 = (double) score.getScore() / mMax;
                        v1 = Math.min(Math.max(v1, 0.0), 1.0);
                    }
                    String studentName = score.getStudent().getFirstName() + " " + score.getStudent().getLastName();
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
