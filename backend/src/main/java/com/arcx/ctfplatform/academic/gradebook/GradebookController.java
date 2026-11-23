package com.arcx.ctfplatform.academic.gradebook;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<List<GradebookRowDto>> getGradebook(
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) UUID flowId
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Access Denied");
        }
        if (isAdminOrInstructor(auth)) {
            return ResponseEntity.ok(gradebookService.getGradebook(groupId, flowId));
        }
        if (hasRole(auth, "STUDENT")) {
            return ResponseEntity.ok(
                    gradebookService.getMyGradebookRow(auth.getName())
                            .map(List::of)
                            .orElseGet(List::of)
            );
        }
        throw new AccessDeniedException("Access Denied");
    }

    private boolean isAdminOrInstructor(Authentication auth) {
        return hasRole(auth, "ADMIN") || hasRole(auth, "INSTRUCTOR");
    }

    private boolean hasRole(Authentication auth, String roleName) {
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        String prefixed = "ROLE_" + roleName;
        return auth.getAuthorities().stream()
                .filter(a -> a != null && a.getAuthority() != null)
                .anyMatch(a -> prefixed.equalsIgnoreCase(a.getAuthority())
                        || roleName.equalsIgnoreCase(a.getAuthority()));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
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
