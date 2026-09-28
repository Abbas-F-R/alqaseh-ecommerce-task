package com.alqaseh.ecommerce.shared.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7Test {

    private static boolean isVersion7(UUID uuid) {
        return uuid != null && uuid.version() == 7 && uuid.variant() == 2;
    }

    @Test
    @DisplayName("Generated UUID must be RFC 9562 Version 7")
    void shouldGenerateVersion7Uuid() {
        UUID uuid = UuidV7Generator.generate();

        assertThat(uuid).isNotNull();
        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2); // IETF variant (10xx in binary)
        assertThat(isVersion7(uuid)).isTrue();
    }

    @Test
    @DisplayName("UUID v7 validator must correctly identify v7 vs v4 or null")
    void shouldValidateUuidVersionCorrectly() {
        UUID v7 = UuidV7Generator.generate();
        UUID v4 = UUID.randomUUID();

        assertThat(isVersion7(v7)).isTrue();
        assertThat(isVersion7(v4)).isFalse();
        assertThat(isVersion7(null)).isFalse();
    }

    @Test
    @DisplayName("UUID v7 timestamp should match current epoch millis")
    void shouldContainValidEpochTimestamp() {
        long beforeMillis = Instant.now().toEpochMilli();
        UUID uuid = UuidV7Generator.generate();
        long afterMillis = Instant.now().toEpochMilli();

        long extractedTimestamp = uuid.getMostSignificantBits() >>> 16;

        assertThat(extractedTimestamp).isGreaterThanOrEqualTo(beforeMillis);
        assertThat(extractedTimestamp).isLessThanOrEqualTo(afterMillis);
    }

    @Test
    @DisplayName("Consecutive UUID v7 values must be chronologically ordered")
    void shouldBeTimeOrdered() throws InterruptedException {
        UUID first = UuidV7Generator.generate();
        Thread.sleep(2);
        UUID second = UuidV7Generator.generate();
        Thread.sleep(2);
        UUID third = UuidV7Generator.generate();

        assertThat(first.compareTo(second)).isLessThan(0);
        assertThat(second.compareTo(third)).isLessThan(0);
    }

    @Test
    @DisplayName("Generating 10,000 UUID v7 values produces zero collisions")
    void shouldGenerateUniqueUuidsWithoutCollision() {
        int count = 10_000;
        Set<UUID> generated = new HashSet<>(count);

        for (int i = 0; i < count; i++) {
            UUID uuid = UuidV7Generator.generate();
            assertThat(uuid.version()).isEqualTo(7);
            generated.add(uuid);
        }

        assertThat(generated).hasSize(count);
    }
}
