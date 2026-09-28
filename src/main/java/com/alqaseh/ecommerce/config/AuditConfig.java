package com.alqaseh.ecommerce.config;

import com.alqaseh.ecommerce.infrastructure.security.SecurityUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/** JPA auditing (createdAt/By, updatedAt/By) and the application {@link Clock}. */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class AuditConfig {

    /** Current user's id, taken from the security context populated by the JWT filter. */
    @Bean
    public AuditorAware<UUID> auditorProvider() {
        return SecurityUtils::getCurrentUserId;
    }

    /** Audit timestamps are truncated to microseconds, the precision PostgreSQL stores, so a response equals what is read back later. */
    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant().truncatedTo(ChronoUnit.MICROS));
    }

    /** Injected wherever "now" matters (e.g. discount expiry) so tests can control time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
