package com.arcx.ctfplatform.audit.service;

import com.arcx.ctfplatform.audit.entity.AuditLog;
import com.arcx.ctfplatform.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditLogRepository);
    }

    @Test
    @DisplayName("Both logEvent overloads use Propagation.REQUIRES_NEW")
    void logEventOverloadsUseRequiresNew() throws NoSuchMethodException {
        Method sixArg = AuditService.class.getMethod(
                "logEvent", UUID.class, String.class, String.class, String.class, String.class, String.class);
        Method eightArg = AuditService.class.getMethod(
                "logEvent", UUID.class, String.class, String.class, String.class, String.class, String.class, String.class, String.class);

        Transactional tx6 = sixArg.getAnnotation(Transactional.class);
        Transactional tx8 = eightArg.getAnnotation(Transactional.class);

        assertThat(tx6).isNotNull();
        assertThat(tx6.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(tx8).isNotNull();
        assertThat(tx8.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    @DisplayName("Truncates bounded columns to DB limits while preserving TEXT fields")
    void truncatesBoundedColumns() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        String longUsername = "u".repeat(150);
        String longRole = "r".repeat(60);
        String longAction = "a".repeat(80);
        String longTarget = "t".repeat(150);
        String longIp = "1".repeat(80);
        String longDetails = "d".repeat(500);

        auditService.logEvent(UUID.randomUUID(), longUsername, longRole, longAction, longTarget, longDetails, longDetails, longIp);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getUsername()).hasSize(100);
        assertThat(saved.getRole()).hasSize(30);
        assertThat(saved.getActionType()).hasSize(50);
        assertThat(saved.getTargetObject()).hasSize(100);
        assertThat(saved.getIpAddress()).hasSize(45);
        assertThat(saved.getOldValue()).isEqualTo(longDetails);
        assertThat(saved.getNewValue()).isEqualTo(longDetails);
    }

    @Test
    @DisplayName("Defaults null or blank actionType to UNKNOWN")
    void defaultsBlankActionType() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        auditService.logEvent(null, "user", "   ", "target", "details", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getActionType()).isEqualTo("UNKNOWN");
    }
}
