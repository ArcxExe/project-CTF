package com.arcx.ctfplatform.attempts.controller;

import com.arcx.ctfplatform.attempts.dto.AttemptResponse;
import com.arcx.ctfplatform.attempts.entity.Attempt;
import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.attempts.service.AttemptService;
import com.arcx.ctfplatform.common.config.IMapping;
import com.arcx.ctfplatform.common.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/attempts")
@PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
@RequiredArgsConstructor
public class AdminAttemptController {

    private final AttemptService attemptService;
    private final AttemptRepository attemptRepository;
    private final IMapping<Attempt, AttemptResponse> attemptMapper;
    private final FileStorageService fileStorageService;

    public record GradeRequest(int score, Double manualPercentMultiplier, Boolean approved, String reason) {
        public GradeRequest(int score, double manualPercentMultiplier) {
            this(score, Double.valueOf(manualPercentMultiplier), true, null);
        }
    }

    @PostMapping("/{id}/grade")
    public ResponseEntity<AttemptResponse> gradeAttempt(
            @PathVariable UUID id,
            @RequestBody GradeRequest request) {
        Attempt graded = attemptService.gradeAttempt(
                id,
                request.score(),
                request.manualPercentMultiplier(),
                request.approved()
        );
        return ResponseEntity.ok(attemptMapper.mapping(graded));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> downloadAttemptFile(@PathVariable UUID id) {
        Attempt attempt = attemptRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Attempt not found"));
        if (attempt.getFilePath() == null || attempt.getFilePath().isBlank()) {
            throw new IllegalArgumentException("No file associated with this attempt");
        }
        Path filePath = fileStorageService.resolveFile(attempt.getFilePath());
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(filePath);
        String filename = filePath.getFileName().toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @GetMapping("/pending-review")
    public ResponseEntity<List<AttemptResponse>> getPendingReviewAttempts() {
        List<Attempt> pendingAttempts = attemptRepository.findAllByFilePathIsNotNullAndIsCorrectFalse();
        return ResponseEntity.ok(attemptMapper.mappingList(pendingAttempts));
    }
}
