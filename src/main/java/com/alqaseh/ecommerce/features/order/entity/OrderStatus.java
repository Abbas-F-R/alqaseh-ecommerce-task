package com.alqaseh.ecommerce.features.order.entity;

/**
 * Only completed orders exist: a failed checkout (declined payment, no stock, ...) is rolled back and leaves no
 * order. Add values here only together with the feature that produces them (e.g. cancellation).
 */
public enum OrderStatus {
    COMPLETED
}
