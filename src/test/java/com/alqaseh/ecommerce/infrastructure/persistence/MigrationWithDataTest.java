package com.alqaseh.ecommerce.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A database that was in use before V9 and V10 holds rows those rules forbid (a padded name, a free product, a huge stock, a padded
 * discount code). The migration must still apply: the new constraints guard every new or changed row at once and stay NOT VALID until
 * the old rows are corrected, after which they can be validated. Needs Docker (Testcontainers starts postgres:17-alpine).
 */
@EnabledIf(value = "dockerAvailable", disabledReason = "No Docker: PostgreSQL migration test skipped")
class MigrationWithDataTest {

    private static PostgreSQLContainer<?> container;
    private static JdbcTemplate jdbc;
    private static DriverManagerDataSource dataSource;

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load();
    }

    @BeforeAll
    static void start() {
        container = new PostgreSQLContainer<>("postgres:17-alpine");
        container.start();
        dataSource = new DriverManagerDataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword());
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterAll
    static void stop() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    @DisplayName("V9 and V10 apply to a database that holds legacy rows; the rules guard new rows at once and validate after the rows are fixed")
    void migrationKeepsLegacyRowsAndGuardsNewOnes() {
        flyway("8").migrate();

        UUID admin = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, username, password, role) VALUES (?, 'legacy_admin', 'x', 'ADMIN')", admin);
        String product = """
                INSERT INTO products (id, name, category, price, cost, available_quantity, version, created_by, created_at, updated_at)
                VALUES (?, ?, 'FURNITURE', ?::numeric, 5, ?, 0, ?, now(), ?::timestamptz)""";
        jdbc.update(product, UUID.randomUUID(), " Padded legacy ", "10", 1, admin, null);
        jdbc.update(product, UUID.randomUUID(), "Free legacy", "0", 1, admin, null);
        jdbc.update(product, UUID.randomUUID(), "Overstocked legacy", "10", 2_000_000, admin, null);
        jdbc.update(product, UUID.randomUUID(), "Time travel legacy", "10", 1, admin, "2000-01-01T00:00:00Z");
        jdbc.update(product, UUID.randomUUID(), "Below cost legacy", "3", 1, admin, null);
        jdbc.update(product, UUID.randomUUID(), "Fine legacy", "10", 1, admin, null);
        jdbc.update("""
                INSERT INTO discount_codes (id, code, amount, minimum_order_total, expires_at, used, version)
                VALUES (?, ' PADDED ', 5, 0, now() + interval '1 day', false, 0)""", UUID.randomUUID());

        flyway("latest").migrate(); // must not fail although the legacy rows break the new rules

        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(6);
        List<String> notValidated = jdbc.queryForList("SELECT conname FROM pg_constraint WHERE NOT convalidated ORDER BY conname", String.class);
        assertThat(notValidated).containsExactly("ck_discount_codes_code_trimmed", "ck_products_cost_within_price", "ck_products_name_trimmed", "ck_products_price",
                "ck_products_quantity_max", "ck_products_updated_after_created");

        // new rows are guarded from the first moment
        assertThatThrownBy(() -> jdbc.update(product, UUID.randomUUID(), " Bad new", "10", 1, admin, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_products_name_trimmed");
        assertThatThrownBy(() -> jdbc.update(product, UUID.randomUUID(), "Free new", "0", 1, admin, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_products_cost_within_price");
        assertThatThrownBy(() -> jdbc.update(product, UUID.randomUUID(), "Below cost new", "4", 1, admin, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_products_cost_within_price");
        // ... and changing a legacy row into another invalid state is refused too
        assertThatThrownBy(() -> jdbc.update("UPDATE products SET available_quantity = 3000000 WHERE name = 'Fine legacy'"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_products_quantity_max");

        // once the legacy rows are corrected, the constraints validate
        jdbc.update("UPDATE products SET name = btrim(name), price = 10, available_quantity = 1, updated_at = NULL");
        jdbc.update("UPDATE discount_codes SET code = btrim(code)");
        for (String constraint : notValidated) {
            String table = constraint.startsWith("ck_discount") ? "discount_codes" : "products";
            jdbc.execute("ALTER TABLE " + table + " VALIDATE CONSTRAINT " + constraint);
        }
        assertThat(jdbc.queryForList("SELECT conname FROM pg_constraint WHERE NOT convalidated", String.class)).isEmpty();
    }
}
