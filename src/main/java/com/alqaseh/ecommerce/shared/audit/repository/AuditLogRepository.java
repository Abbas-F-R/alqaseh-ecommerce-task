package com.alqaseh.ecommerce.shared.audit.repository;

import com.alqaseh.ecommerce.shared.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
