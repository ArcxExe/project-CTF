package com.arcx.ctfplatform.audit.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.arcx.ctfplatform.audit.entity.AuditLog;
import com.arcx.ctfplatform.audit.repository.AuditLogRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditService {

    private static final int MAX_USERNAME_LENGTH = 100;
    private static final int MAX_ROLE_LENGTH = 30;
    private static final int MAX_ACTION_TYPE_LENGTH = 50;
    private static final int MAX_TARGET_OBJECT_LENGTH = 100;
    private static final int MAX_IP_ADDRESS_LENGTH = 45;

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog logEvent(UUID userId, String username, String actionType,
                             String targetObject, String details, String ipAddress) {
        return logEvent(userId, username, null, actionType, targetObject, null, details, ipAddress);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog logEvent(UUID userId, String username, String role, String actionType,
                             String targetObject, String oldValue, String newValue, String ipAddress) {
        String normalizedActionType = (actionType == null || actionType.isBlank()) ? "UNKNOWN" : actionType;
        AuditLog entry = AuditLog.builder()
                .timestamp(Instant.now())
                .userId(userId)
                .username(truncate(username, MAX_USERNAME_LENGTH))
                .role(truncate(role, MAX_ROLE_LENGTH))
                .actionType(truncate(normalizedActionType, MAX_ACTION_TYPE_LENGTH))
                .targetObject(truncate(targetObject, MAX_TARGET_OBJECT_LENGTH))
                .oldValue(oldValue)
                .newValue(newValue)
                .ipAddress(truncate(ipAddress, MAX_IP_ADDRESS_LENGTH))
                .build();
        return auditLogRepository.save(entry);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
