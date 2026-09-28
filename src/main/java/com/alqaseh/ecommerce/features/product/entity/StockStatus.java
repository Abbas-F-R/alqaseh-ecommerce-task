package com.alqaseh.ecommerce.features.product.entity;

/**
 * Coarse stock level shown to customers instead of the exact quantity:
 * 0-4 low, 5-9 limited, 10+ available (spelled in lower case in the API).
 */
public enum StockStatus {
    LOW,
    LIMITED,
    AVAILABLE;

    @com.fasterxml.jackson.annotation.JsonValue
    public String value() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    private static final int LOW_MAX = 4;
    private static final int LIMITED_MAX = 9;

    public static StockStatus fromQuantity(int quantity) {
        if (quantity <= LOW_MAX) {
            return LOW;
        }
        return quantity <= LIMITED_MAX ? LIMITED : AVAILABLE;
    }
}
