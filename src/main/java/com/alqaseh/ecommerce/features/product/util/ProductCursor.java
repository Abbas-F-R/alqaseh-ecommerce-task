package com.alqaseh.ecommerce.features.product.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Encodes and decodes opaque Base64 cursors for product Keyset/Seek pagination.
 */
public final class ProductCursor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private record CursorPayload(@JsonProperty("id") UUID id) {
    }

    private ProductCursor() {
    }

    public static String encode(UUID id) {
        if (id == null) {
            return null;
        }
        try {
            String json = OBJECT_MAPPER.writeValueAsString(new CursorPayload(id));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode cursor", e);
        }
    }

    public static UUID decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            byte[] bytes;
            String trimmed = cursor.trim();
            try {
                bytes = Base64.getUrlDecoder().decode(trimmed);
            } catch (IllegalArgumentException e) {
                bytes = Base64.getDecoder().decode(trimmed);
            }
            String json = new String(bytes, StandardCharsets.UTF_8);
            CursorPayload payload = OBJECT_MAPPER.readValue(json, CursorPayload.class);
            if (payload == null || payload.id() == null) {
                throw new IllegalArgumentException("Invalid cursor format");
            }
            return payload.id();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor: " + cursor, e);
        }
    }

    public static boolean isValid(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return true;
        }
        try {
            decode(cursor);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
