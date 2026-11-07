package com.arcx.ctfplatform.leaderboard.controller;

import com.arcx.ctfplatform.leaderboard.service.LeaderboardService;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final LeaderboardService leaderboardService;
    private final StudentRepository studentRepository;

    @GetMapping("/v2")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<String> exportV2Metrics() {
        List<LeaderboardService.LeaderboardEntry> leaderboard = leaderboardService.getLeaderboardSnapshot(null, null);
        List<Student> students = studentRepository.findAll();
        
        Map<UUID, Student> studentMap = students.stream().collect(Collectors.toMap(Student::getId, s -> s));

        // Group by groupId
        Map<UUID, List<LeaderboardService.LeaderboardEntry>> byGroup = leaderboard.stream()
                .filter(e -> studentMap.get(e.studentId()) != null && studentMap.get(e.studentId()).getGroupId() != null)
                .collect(Collectors.groupingBy(e -> studentMap.get(e.studentId()).getGroupId()));

        StringBuilder csv = new StringBuilder("\uFEFFStudent_ID,Username,FullName,Group_ID,Group_Name,CTF_Score,Max_Group_Score,V2_Metric,L_Percent,K_Percent,C_Percent,S_Score,Grade\n");

        for (Map.Entry<UUID, List<LeaderboardService.LeaderboardEntry>> entry : byGroup.entrySet()) {
            UUID groupId = entry.getKey();
            List<LeaderboardService.LeaderboardEntry> groupEntries = entry.getValue();

            int maxScore = groupEntries.stream().mapToInt(LeaderboardService.LeaderboardEntry::ctfScore).max().orElse(0);

            for (LeaderboardService.LeaderboardEntry le : groupEntries) {
                Student st = studentMap.get(le.studentId());
                String fio = st != null ? st.getFullName().replace(",", " ") : "";
                String groupName = le.groupName() != null ? le.groupName().replace(",", " ") : "";
                double v2 = maxScore == 0 ? 0.0 : (double) le.ctfScore() / maxScore;
                int numericGrade = (le.isEligible() && le.recommendedGrade() != null) ? le.recommendedGrade() : 2;
                csv.append(String.format(Locale.ROOT, "%s,%s,%s,%s,%s,%d,%d,%.4f,%.2f,%.2f,%.2f,%.2f,%d\n",
                        le.studentId(), le.username(), fio, groupId, groupName, le.ctfScore(), maxScore, v2,
                        le.lPercent(), le.kPercent(), le.cPercent(), le.sScore(), numericGrade));
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", "v2_metrics.csv");

        return ResponseEntity.ok()
                .headers(headers)
                .body(csv.toString());
    }
}
