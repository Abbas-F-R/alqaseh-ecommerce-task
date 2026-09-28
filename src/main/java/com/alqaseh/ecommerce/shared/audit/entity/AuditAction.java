package com.alqaseh.ecommerce.shared.audit.entity;

import lombok.Getter;

/** Business events recorded in the audit log, each tied to the kind of entity it concerns. */
@Getter
public enum AuditAction {
    PRODUCT_CREATED("PRODUCT"),
    PRODUCT_UPDATED("PRODUCT"),
    ORDER_CREATED("ORDER"),
    DISCOUNT_APPLIED("DISCOUNT");

    private final String entityType;

    AuditAction(String entityType) {
        this.entityType = entityType;
    }
}
