package com.arcx.ctfplatform.audit.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arcx.ctfplatform.audit.entity.AuditLog;
import com.arcx.ctfplatform.audit.repository.AuditLogRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public AuditLog logEvent(UUID userId, String username, String role, String actionType,
                             String targetObject, String oldValue, String newValue, String ipAddress) {
        AuditLog entry = AuditLog.builder()
                .timestamp(Instant.now())
                .userId(userId)
                .username(username)
                .role(role)
                .actionType(actionType)
                .targetObject(targetObject)
                .oldValue(oldValue)
                .newValue(newValue)
                .ipAddress(ipAddress)
                .build();
        return auditLogRepository.save(entry);
    }
}
