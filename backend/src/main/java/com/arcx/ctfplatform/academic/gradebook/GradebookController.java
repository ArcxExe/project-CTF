package com.arcx.ctfplatform.academic.gradebook;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gradebook")
@RequiredArgsConstructor
public class GradebookController {

    private final GradebookService gradebookService;

    @GetMapping
    public ResponseEntity<List<GradebookRowDto>> getGradebook(
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) UUID flowId
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && isStudentOnly(auth)) {
            return ResponseEntity.ok(
                    gradebookService.getMyGradebookRow(auth.getName())
                            .map(List::of)
                            .orElseGet(List::of)
            );
        }
        return ResponseEntity.ok(gradebookService.getGradebook(groupId, flowId));
    }

    private boolean isStudentOnly(Authentication auth) {
        if (auth.getAuthorities() == null || auth.getAuthorities().isEmpty()) {
            return false;
        }
        boolean isAdminOrInstructor = auth.getAuthorities().stream()
                .anyMatch(a -> {
                    String role = a.getAuthority().toUpperCase();
                    return role.contains("ADMIN") || role.contains("INSTRUCTOR");
                });
        return !isAdminOrInstructor;
    }

    @GetMapping("/me")
    public ResponseEntity<GradebookRowDto> getMyGradebookRow(Authentication auth) {
        String username = auth != null ? auth.getName() : null;
        return gradebookService.getMyGradebookRow(username)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<GradebookRowDto> updateStudentGradebook(
            @PathVariable UUID studentId,
            @RequestBody GradebookUpdateRequest request
    ) {
        return ResponseEntity.ok(gradebookService.updateStudentGradebook(studentId, request));
    }

    @PutMapping("/lab-detail")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<GradebookRowDto> updateLabDetail(
            @RequestBody LabDetailUpdateRequest request
    ) {
        return ResponseEntity.ok(gradebookService.updateLabDetail(request));
    }

    @GetMapping("/export/csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) UUID flowId
    ) {
        byte[] csvBytes = gradebookService.exportCsv(groupId, flowId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rating_gradebook.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvBytes);
    }

    @GetMapping("/export/xlsx")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<byte[]> exportXlsx(
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) UUID flowId
    ) {
        byte[] xlsxBytes = gradebookService.exportXlsx(groupId, flowId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rating_gradebook.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsxBytes);
    }
}
