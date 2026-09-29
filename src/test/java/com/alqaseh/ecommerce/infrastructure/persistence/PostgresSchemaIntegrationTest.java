package com.alqaseh.ecommerce.infrastructure.persistence;

import com.alqaseh.ecommerce.features.discount.entity.DiscountCode;
import com.alqaseh.ecommerce.features.discount.repository.DiscountCodeRepository;
import com.alqaseh.ecommerce.features.product.entity.Product;
import com.alqaseh.ecommerce.features.product.entity.ProductCategory;
import com.alqaseh.ecommerce.features.product.repository.ProductRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Runs the REAL Flyway migrations on PostgreSQL and lets Hibernate validate its mapping against the result
 * ({@code ddl-auto=validate}). Everything PostgreSQL-specific is checked here because the other integration tests use
 * H2 with a schema generated from the entities: partial/functional unique indexes, the index set, and the
 * unique-violation to HTTP 409 mapping under a genuine race.
 *
 * <p>Needs a PostgreSQL: a Docker daemon (Testcontainers starts {@code postgres:17-alpine}) or an existing server given as
 * {@code -Dtest.postgres.url=jdbc:postgresql://host:port/db -Dtest.postgres.user=... -Dtest.postgres.password=...}.
 * Without either, the class is skipped.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIf(value = "postgresAvailable", disabledReason = "No Docker and no -Dtest.postgres.url: PostgreSQL tests skipped")
class PostgresSchemaIntegrationTest {

    private static final String EXTERNAL_URL = System.getProperty("test.postgres.url");
    private static PostgreSQLContainer<?> container;

    static boolean postgresAvailable() {
        return EXTERNAL_URL != null || DockerClientFactory.instance().isDockerAvailable();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (EXTERNAL_URL != null) {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username", () -> System.getProperty("test.postgres.user", "postgres"));
            registry.add("spring.datasource.password", () -> System.getProperty("test.postgres.password", "dev_only_fake_password"));
        } else {
            container = new PostgreSQLContainer<>("postgres:17-alpine");
            container.start();
            registry.add("spring.datasource.url", container::getJdbcUrl);
            registry.add("spring.datasource.username", container::getUsername);
            registry.add("spring.datasource.password", container::getPassword);
        }
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @AfterAll
    static void stopContainer() {
        if (container != null) {
            container.stop();
        }
    }

    @Autowired
    private Flyway flyway;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private DiscountCodeRepository discountCodeRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM users WHERE username LIKE 'ck%'");
        productRepository.deleteAll();
        discountCodeRepository.deleteAll();
    }

    private static Product product(String name) {
        return Product.builder().name(name).category(ProductCategory.FURNITURE)
                .price(BigDecimal.TEN).cost(BigDecimal.ONE).availableQuantity(5).build();
    }

    @Test
    @DisplayName("All migrations apply and the schema they produce satisfies Hibernate's validation")
    void migrationsApplyAndSchemaMatchesEntities() {
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("4");
    }

    @Test
    @DisplayName("The index set is exactly the intended one (no forgotten or duplicate indexes)")
    void indexSet() {
        List<String> indexes = jdbc.queryForList("""
                SELECT indexname FROM pg_indexes
                WHERE schemaname = 'public' AND indexname NOT LIKE '%pkey' AND tablename <> 'flyway_schema_history'
                ORDER BY indexname""", String.class);

        assertThat(indexes).containsExactly(
                "idx_audit_logs_entity",
                "idx_orders_created_at",
                "idx_orders_customer_created",
                "idx_orders_payment_created",
                "idx_products_category",
                "uq_discount_codes_code",
                "uq_order_items_order_product",
                "uq_orders_discount_code",
                "uq_products_name",
                "uq_users_username");
    }

    @Test
    @DisplayName("Product names are unique ignoring case")
    void productNameUniqueness() {
        productRepository.saveAndFlush(product("Chair"));

        assertThatThrownBy(() -> productRepository.saveAndFlush(product("CHAIR")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_products_name");

    }

    @Test
    @DisplayName("Discount codes are unique")
    void discountCodeUniqueness() {
        DiscountCode.DiscountCodeBuilder<?, ?> code = DiscountCode.builder().code("SAVE10").amount(BigDecimal.TEN)
                .minimumOrderTotal(BigDecimal.ZERO).expiresAt(Instant.now().plusSeconds(3600));
        discountCodeRepository.saveAndFlush(code.build());

        assertThatThrownBy(() -> discountCodeRepository.saveAndFlush(code.build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_discount_codes_code");
    }

    @Test
    @DisplayName("Genuine race: the pre-check passes for both requests, the unique index rejects one, the client gets 409")
    void duplicateProductNameRaceReturnsConflict() throws Exception {
        String body = """
                {"name":"Race Desk","category":"FURNITURE","price":100,"cost":50,"availableQuantity":5}""";

        CompletableFuture<Integer> loser = new CompletableFuture<>();
        transactionTemplate.executeWithoutResult(status -> {
            // 1. Insert the name inside a transaction that stays uncommitted for a moment.
            productRepository.saveAndFlush(product("Race Desk"));

            // 2. A concurrent request: its existence check cannot see the uncommitted row, so it passes and the
            //    INSERT blocks on the unique index until we commit.
            CompletableFuture.runAsync(() -> {
                try {
                    loser.complete(mockMvc.perform(post("/api/products").with(user("admin").roles("ADMIN"))
                                    .contentType(MediaType.APPLICATION_JSON).content(body))
                            .andReturn().getResponse().getStatus());
                } catch (Exception e) {
                    loser.completeExceptionally(e);
                }
            });
            sleep(1000);
        }); // 3. commit: the blocked INSERT now fails with a unique violation

        assertThat(loser.get(15, TimeUnit.SECONDS)).isEqualTo(409);
        assertThat(productRepository.findAll()).hasSize(1);
    }

    // ---- integrity constraints (V4): the database refuses what the assignment forbids, whoever writes the row

    private UUID insertUser(String username, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, username, password, role) VALUES (?, ?, 'x', ?)", id, username, role);
        return id;
    }

    private UUID insertProduct(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products (id, name, category, price, cost, available_quantity, version)
                VALUES (?, ?, 'FURNITURE', 10, 5, 5, 0)""", id, name);
        return id;
    }

    private UUID insertDiscountCode(String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO discount_codes (id, code, amount, minimum_order_total, expires_at, used, version)
                VALUES (?, ?, 5, 0, now() + interval '1 day', false, 0)""", id, code);
        return id;
    }

    private UUID insertOrder(UUID customer, String subtotal, String discount, String total, UUID codeId, String method, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO orders (id, customer_id, subtotal_amount, discount_amount, total_amount, total_cost,
                                    discount_code_id, payment_method, status)
                VALUES (?, ?, ?::numeric, ?::numeric, ?::numeric, 1, ?, ?, ?)""",
                id, customer, subtotal, discount, total, codeId, method, status);
        return id;
    }

    private void assertRejected(Runnable statement, String constraint) {
        assertThatThrownBy(statement::run).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(constraint);
    }

    @Test
    @DisplayName("Only the four categories, the two payment methods, the two roles and the one order status are accepted")
    void fixedVocabularies() {
        UUID admin = insertUser("ck_vocab", "ADMIN");

        assertRejected(() -> jdbc.update("""
                INSERT INTO products (id, name, category, price, cost, available_quantity, version)
                VALUES (?, 'Toy', 'TOYS', 10, 5, 5, 0)""", UUID.randomUUID()), "ck_products_category");
        assertRejected(() -> insertUser("ck_role", "OWNER"), "ck_users_role");
        assertRejected(() -> insertOrder(admin, "10", "0", "10", null, "BITCOIN", "COMPLETED"), "ck_orders_payment_method");
        assertRejected(() -> insertOrder(admin, "10", "0", "10", null, "CREDIT_CARD", "PENDING"), "ck_orders_status");
    }

    @Test
    @DisplayName("Blank product names, blank usernames and lower-case discount codes are rejected")
    void nonBlankNamesAndUpperCaseCodes() {
        assertRejected(() -> insertProduct("   "), "ck_products_name_not_blank");
        assertRejected(() -> insertUser("   ", "CUSTOMER"), "ck_users_username_not_blank");
        assertRejected(() -> insertDiscountCode("save5"), "ck_discount_codes_code");
    }

    @Test
    @DisplayName("Created-by / updated-by must reference an existing user")
    void auditColumnsReferenceUsers() {
        UUID nobody = UUID.randomUUID();

        assertRejected(() -> jdbc.update("""
                INSERT INTO products (id, name, category, price, cost, available_quantity, version, created_by)
                VALUES (?, 'Ghost Made', 'FURNITURE', 10, 5, 5, 0, ?)""", UUID.randomUUID(), nobody), "fk_products_created_by");

        UUID product = insertProduct("Real Product");
        assertRejected(() -> jdbc.update("UPDATE products SET updated_by = ? WHERE id = ?", nobody, product), "fk_products_updated_by");

        UUID admin = insertUser("ck_admin", "ADMIN");
        jdbc.update("UPDATE products SET created_by = ?, updated_by = ? WHERE id = ?", admin, admin, product);
    }

    @Test
    @DisplayName("Order amounts must add up, and a discount exists exactly when a code was used")
    void orderAmountsAreConsistent() {
        UUID customer = insertUser("ck_amounts", "CUSTOMER");
        UUID code = insertDiscountCode("AMOUNTS5");

        assertRejected(() -> insertOrder(customer, "100", "5", "90", code, "CREDIT_CARD", "COMPLETED"), "ck_orders_totals");
        assertRejected(() -> insertOrder(customer, "100", "5", "95", null, "CREDIT_CARD", "COMPLETED"), "ck_orders_discount_consistency");
        assertRejected(() -> insertOrder(customer, "100", "0", "100", code, "CREDIT_CARD", "COMPLETED"), "ck_orders_discount_consistency");
        assertRejected(() -> insertOrder(customer, "3", "5", "-2", code, "CREDIT_CARD", "COMPLETED"), "ck_orders_total_amount");

        insertOrder(customer, "100", "5", "95", code, "CREDIT_CARD", "COMPLETED");
    }

    @Test
    @DisplayName("A discount code can be attached to only one order (single use is guaranteed by the database)")
    void discountCodeSingleUseInTheDatabase() {
        UUID customer = insertUser("ck_single_use", "CUSTOMER");
        UUID code = insertDiscountCode("ONCE5");
        insertOrder(customer, "100", "5", "95", code, "CREDIT_CARD", "COMPLETED");

        assertRejected(() -> insertOrder(customer, "100", "5", "95", code, "XYZ_WALLET", "COMPLETED"), "uq_orders_discount_code");
    }

    @Test
    @DisplayName("An order has at most one line per product; quantities must be positive")
    void orderLines() {
        UUID customer = insertUser("ck_lines", "CUSTOMER");
        UUID product = insertProduct("Line Product");
        UUID order = insertOrder(customer, "10", "0", "10", null, "CREDIT_CARD", "COMPLETED");
        String insertLine = "INSERT INTO order_items (id, order_id, product_id, product_name, unit_price, unit_cost, quantity) VALUES (?, ?, ?, 'p', 10, 5, ?)";

        assertRejected(() -> jdbc.update(insertLine, UUID.randomUUID(), order, product, 0), "ck_order_items_quantity");
        jdbc.update(insertLine, UUID.randomUUID(), order, product, 1);
        assertRejected(() -> jdbc.update(insertLine, UUID.randomUUID(), order, product, 2), "uq_order_items_order_product");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
