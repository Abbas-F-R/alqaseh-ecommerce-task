package com.alqaseh.ecommerce.shared.audit;

import com.alqaseh.ecommerce.features.product.entity.Product;
import com.alqaseh.ecommerce.features.product.entity.ProductCategory;
import com.alqaseh.ecommerce.features.product.repository.ProductRepository;
import com.alqaseh.ecommerce.infrastructure.user.entity.User;
import com.alqaseh.ecommerce.infrastructure.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SpringAuditingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("JPA Auditing automatically sets createdAt, updatedAt, createdBy, updatedBy on persist")
    void shouldAutomaticallyPopulateAuditFieldsOnPersist() {
        User adminUser = userRepository.findByUsername("admin").orElseThrow();
        UUID adminId = adminUser.getId();

        Product product = Product.builder()
                .name("Audited Mouse")
                .category(ProductCategory.ELECTRONICS)
                .price(BigDecimal.valueOf(45.00))
                .cost(BigDecimal.valueOf(20.00))
                .availableQuantity(50)
                .build();

        Product saved = productRepository.saveAndFlush(product);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId().version()).isEqualTo(7);

        // Verification of JPA Auditing @CreatedDate and @LastModifiedDate
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isBetween(Instant.now().minusSeconds(10), Instant.now().plusSeconds(10));
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(saved.getCreatedAt().getNano() % 1000).as("stored precision is microseconds").isZero();

        // Verification of AuditorAware<UUID> from Spring Security UserPrincipal
        assertThat(saved.getCreatedBy()).isNotNull();
        assertThat(saved.getCreatedBy()).isEqualTo(adminId);
        assertThat(saved.getUpdatedBy()).isEqualTo(adminId);
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("JPA Auditing updates updatedAt and updatedBy on entity modification")
    void shouldUpdateAuditFieldsOnModification() throws InterruptedException {
        User adminUser = userRepository.findByUsername("admin").orElseThrow();
        UUID adminId = adminUser.getId();

        Product product = Product.builder()
                .name("Initial Name")
                .category(ProductCategory.ELECTRONICS)
                .price(BigDecimal.valueOf(100.00))
                .cost(BigDecimal.valueOf(50.00))
                .availableQuantity(10)
                .build();

        Product saved = productRepository.saveAndFlush(product);
        Instant originalCreatedAt = saved.getCreatedAt();
        UUID originalCreatedBy = saved.getCreatedBy();

        Thread.sleep(10);

        saved.setName("Updated Name");
        saved.setPrice(BigDecimal.valueOf(120.00));
        Product updated = productRepository.saveAndFlush(saved);

        assertThat(updated.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(updated.getCreatedBy()).isEqualTo(originalCreatedBy);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(originalCreatedAt);
        assertThat(updated.getUpdatedBy()).isEqualTo(adminId);
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("Client cannot tamper with audit fields via Request Body")
    void clientCannotTamperWithAuditFields() throws Exception {
        User adminUser = userRepository.findByUsername("admin").orElseThrow();
        UUID adminId = adminUser.getId();

        UUID forgedUserId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        Instant forgedTime = Instant.parse("2020-01-01T00:00:00Z");

        Map<String, Object> maliciousPayload = Map.of(
                "name", "Tamper Resistant Screen",
                "category", "ELECTRONICS",
                "price", 299.99,
                "cost", 150.00,
                "availableQuantity", 10,
                // Attempt to inject audit fields
                "createdBy", forgedUserId.toString(),
                "createdAt", forgedTime.toString(),
                "updatedBy", forgedUserId.toString(),
                "updatedAt", forgedTime.toString()
        );

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maliciousPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.createdBy", is(adminId.toString())))
                .andExpect(jsonPath("$.data.createdBy", not(forgedUserId.toString())))
                .andExpect(jsonPath("$.data.createdAt", not(forgedTime.toString())));

        Product persisted = productRepository.findAll().stream().filter(p -> p.getName().equals("Tamper Resistant Screen")).findFirst().orElseThrow();
        assertThat(persisted.getCreatedBy()).isEqualTo(adminId);
        assertThat(persisted.getCreatedBy()).isNotEqualTo(forgedUserId);
    }
}
