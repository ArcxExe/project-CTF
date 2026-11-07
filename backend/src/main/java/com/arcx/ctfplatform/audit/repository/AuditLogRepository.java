package com.arcx.ctfplatform.audit.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.arcx.ctfplatform.audit.entity.AuditLog;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
