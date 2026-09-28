package com.alqaseh.ecommerce.shared.util;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.uuid.UuidValueGenerator;

import java.util.UUID;

/**
 * Hibernate id generator producing RFC 9562 UUID v7 (time-ordered, index friendly) via FasterXML JUG.
 * Hibernate 6.6 (Spring Boot 3.x) has no built-in v7 style, so this is the smallest way to get one.
 */
public class UuidV7Generator implements UuidValueGenerator {

    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    public static UUID generate() {
        return GENERATOR.generate();
    }

    @Override
    public UUID generateUuid(SharedSessionContractImplementor session) {
        return generate();
    }
}
