package com.arcx.ctfplatform.audit.controller;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arcx.ctfplatform.audit.entity.AuditLog;
import com.arcx.ctfplatform.audit.repository.AuditLogRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public record AuditLogDto(
            UUID id,
            Instant timestamp,
            UUID userId,
            String username,
            String user,
            String role,
            String actionType,
            String action,
            String targetObject,
            String target,
            String oldValue,
            String newValue,
            String details,
            String ipAddress,
            String ip
    ) {
        public static AuditLogDto from(AuditLog log) {
            String uname = log.getUsername() != null ? log.getUsername() : "system";
            String act = log.getActionType() != null ? log.getActionType() : "";
            String tgt = log.getTargetObject() != null ? log.getTargetObject() : "";
            String oldVal = log.getOldValue();
            String newVal = log.getNewValue();
            String det;
            if (oldVal != null && !oldVal.isBlank() && newVal != null && !newVal.isBlank()) {
                det = oldVal + " -> " + newVal;
            } else if (oldVal != null && !oldVal.isBlank()) {
                det = oldVal;
            } else if (newVal != null && !newVal.isBlank()) {
                det = newVal;
            } else {
                det = "";
            }
            String ipAddr = log.getIpAddress() != null ? log.getIpAddress() : "";
            return new AuditLogDto(
                    log.getId(),
                    log.getTimestamp(),
                    log.getUserId(),
                    uname,
                    uname,
                    log.getRole(),
                    act,
                    act,
                    tgt,
                    tgt,
                    oldVal,
                    newVal,
                    det,
                    ipAddr,
                    ipAddr
            );
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<List<AuditLogDto>> getAll() {
        List<AuditLogDto> items = auditLogRepository.findAll().stream()
                .sorted(Comparator.comparing(AuditLog::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(AuditLogDto::from)
                .toList();
        return ResponseEntity.ok(items);
    }
}
