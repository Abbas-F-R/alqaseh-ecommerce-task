package com.alqaseh.ecommerce.features.product.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * The four fixed categories. The API spells them in lower case ({@code furniture}); input is accepted in any letter case.
 * The database stores the constant name.
 */
public enum ProductCategory {
    FURNITURE,
    ELECTRONICS,
    BEAUTY,
    GARDEN;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static ProductCategory from(String text) {
        return valueOf(text.trim().toUpperCase(Locale.ROOT));
    }
}
