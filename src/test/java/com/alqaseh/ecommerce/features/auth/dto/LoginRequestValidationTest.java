package com.alqaseh.ecommerce.features.auth.dto;

import com.alqaseh.ecommerce.features.auth.dto.request.LoginRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Login input is bounded and made of harmless characters before anything looks the user up or logs the name. */
class LoginRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static boolean valid(String username, String password) {
        return validator.validate(LoginRequest.builder().username(username).password(password).build()).isEmpty();
    }

    @Test
    @DisplayName("A normal login is valid, and so are the boundaries: 50 characters of username and 128 of password")
    void validAndBoundaries() {
        assertThat(valid("admin", "Admin123!")).isTrue();
        assertThat(valid("customer.one@shop-1_x", "p")).isTrue();
        assertThat(valid("a".repeat(50), "p".repeat(128))).isTrue();
    }

    @Test
    @DisplayName("Null, empty and blank username or password are rejected")
    void nullEmptyBlank() {
        assertThat(valid(null, "x")).isFalse();
        assertThat(valid("", "x")).isFalse();
        assertThat(valid("   ", "x")).isFalse();
        assertThat(valid("admin", null)).isFalse();
        assertThat(valid("admin", "")).isFalse();
        assertThat(valid("admin", "   ")).isFalse();
    }

    @Test
    @DisplayName("Over the maximum: 51 characters of username, 129 of password")
    void tooLong() {
        assertThat(valid("a".repeat(51), "x")).isFalse();
        assertThat(valid("admin", "p".repeat(129))).isFalse();
        assertThat(valid("admin", "p".repeat(1_000_000))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ad min", "admin\n", "admin\r\nINFO forged log line", "admin\t", "adm/in", "adm'in", "adm;in", "adm\u0000in", "أدمن"})
    @DisplayName("Whitespace, control characters and other symbols are not allowed in a username")
    void invalidCharacters(String username) {
        assertThat(valid(username, "x")).isFalse();
    }
}
