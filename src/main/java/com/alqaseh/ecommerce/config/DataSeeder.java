package com.alqaseh.ecommerce.config;

import com.alqaseh.ecommerce.infrastructure.user.entity.Role;
import com.alqaseh.ecommerce.infrastructure.user.entity.User;
import com.alqaseh.ecommerce.infrastructure.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates well-known demo accounts. Local development and tests only: it must never run in production,
 * where these passwords would be public knowledge.
 */
@Slf4j
@Component
@Profile({"dev", "test"})
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedIfMissing("admin", "Admin123!", Role.ADMIN);
        seedIfMissing("customer1", "Customer123!", Role.CUSTOMER);
        seedIfMissing("customer2", "Customer123!", Role.CUSTOMER);
    }

    private void seedIfMissing(String username, String rawPassword, Role role) {
        if (!userRepository.existsByUsername(username)) {
            userRepository.save(User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(rawPassword))
                    .role(role)
                    .build());
            log.info("Seeded demo user '{}' ({})", username, role);
        }
    }
}
