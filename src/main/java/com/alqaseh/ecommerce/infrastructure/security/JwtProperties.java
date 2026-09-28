package com.alqaseh.ecommerce.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT settings. The application refuses to start without a secret, so a missing
 * {@code JWT_SECRET} in production is a startup error, not a silently insecure default.
 *
 * @param secretKey    Base64-encoded HMAC key (at least 256 bits)
 * @param expirationMs token lifetime in milliseconds
 */
@Validated
@ConfigurationProperties(prefix = "application.security.jwt")
public record JwtProperties(@NotBlank String secretKey, @Positive long expirationMs) {
}
