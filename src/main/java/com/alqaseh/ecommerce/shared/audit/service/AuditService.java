package com.alqaseh.ecommerce.shared.audit.service;

import com.alqaseh.ecommerce.infrastructure.security.SecurityUtils;
import com.alqaseh.ecommerce.shared.audit.entity.AuditAction;
import com.alqaseh.ecommerce.shared.audit.entity.AuditLog;
import com.alqaseh.ecommerce.shared.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private static final String SYSTEM_ACTOR = "SYSTEM";

    private final AuditLogRepository auditLogRepository;

    /** Joins the caller's transaction: the audit row commits or rolls back together with the business change. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAudit(AuditAction action, UUID entityId, String details) {
        auditLogRepository.save(AuditLog.builder()
                .userId(SecurityUtils.getCurrentUsername().orElse(SYSTEM_ACTOR))
                .action(action)
                .entityType(action.getEntityType())
                .entityId(entityId.toString())
                .details(details)
                .build());
    }
}
