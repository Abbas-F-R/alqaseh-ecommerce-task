package com.alqaseh.ecommerce.shared.util;

/** Builds a plain "contains" LIKE pattern: the wildcards typed by the client ({@code %}, {@code _}) are matched literally. */
public final class LikePattern {

    public static final char ESCAPE = '\\';

    private LikePattern() {
    }

    /** Lower-cased pattern for {@code lower(column) like :pattern escape '\'}. */
    public static String contains(String text) {
        String escaped = text.trim().toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
