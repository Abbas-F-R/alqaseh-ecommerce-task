package com.alqaseh.ecommerce.features.product.controller;

import com.alqaseh.ecommerce.features.product.dto.request.ProductRequest;
import com.alqaseh.ecommerce.features.product.entity.Product;
import com.alqaseh.ecommerce.features.product.entity.ProductCategory;
import com.alqaseh.ecommerce.features.product.repository.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.alqaseh.ecommerce.features.order.repository.OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("Admin can create a new product")
    void adminCanCreateProduct() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .name("Ergonomic Keyboard")
                .category(ProductCategory.ELECTRONICS)
                .price(BigDecimal.valueOf(120.00))
                .cost(BigDecimal.valueOf(70.00))
                .availableQuantity(15)
                .build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name", is("Ergonomic Keyboard")))
                .andExpect(jsonPath("$.data.cost", is(70.0)))
                .andExpect(jsonPath("$.data.availableQuantity", is(15)))
                .andExpect(jsonPath("$.data.createdBy", notNullValue()));
    }

    @Test
    @WithMockUser(username = "customer1", roles = {"CUSTOMER"})
    @DisplayName("Customer cannot create a product (403 Forbidden)")
    void customerCannotCreateProduct() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .name("Gaming Monitor")
                .category(ProductCategory.ELECTRONICS)
                .price(BigDecimal.valueOf(300.00))
                .cost(BigDecimal.valueOf(200.00))
                .availableQuantity(5)
                .build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("Unauthenticated request cannot create a product (401 Unauthorized)")
    void unauthenticatedCannotCreateProduct() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .name("Tablet")
                .category(ProductCategory.ELECTRONICS)
                .price(BigDecimal.valueOf(400.00))
                .cost(BigDecimal.valueOf(250.00))
                .availableQuantity(10)
                .build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }

    @Test
    @WithMockUser(username = "customer1", roles = {"CUSTOMER"})
    @DisplayName("Customer listing masks exact quantity and cost, showing stock status")
    void customerListingMasksCostAndQuantity() throws Exception {
        Product product = Product.builder()
                .name("Luxury Perfume")
                .category(ProductCategory.BEAUTY)
                .price(BigDecimal.valueOf(85.00))
                .cost(BigDecimal.valueOf(40.00))
                .availableQuantity(3) // 3 <= 4 -> LOW
                .build();
        productRepository.save(product);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name", is("Luxury Perfume")))
                .andExpect(jsonPath("$.data.content[0].stockStatus", is("low")))
                .andExpect(jsonPath("$.data.content[0].cost").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].availableQuantity").doesNotExist());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Admin listing displays exact quantity and cost")
    void adminListingShowsCostAndQuantity() throws Exception {
        Product product = Product.builder()
                .name("Luxury Perfume")
                .category(ProductCategory.BEAUTY)
                .price(BigDecimal.valueOf(85.00))
                .cost(BigDecimal.valueOf(40.00))
                .availableQuantity(12) // AVAILABLE
                .build();
        productRepository.save(product);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name", is("Luxury Perfume")))
                .andExpect(jsonPath("$.data.content[0].cost", is(40.0)))
                .andExpect(jsonPath("$.data.content[0].availableQuantity", is(12)));
    }

    @Test
    @WithMockUser(username = "customer1", roles = {"CUSTOMER"})
    @DisplayName("Arabic localization via Accept-Language header")
    void arabicLocalizationSupport() throws Exception {
        ProductRequest invalidRequest = ProductRequest.builder().build();

        mockMvc.perform(post("/api/products")
                        .header("Accept-Language", "ar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.message", containsString("فشل التحقق")));
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("Update response carries the refreshed updatedAt/updatedBy, not the values from before the update")
    void updateResponseShowsRefreshedAuditFields() throws Exception {
        Product product = productRepository.saveAndFlush(Product.builder().name("Audit Lamp").category(ProductCategory.GARDEN)
                .price(BigDecimal.valueOf(10)).cost(BigDecimal.valueOf(5)).availableQuantity(3).build());
        Instant before = product.getUpdatedAt();
        Thread.sleep(5);

        String body = mockMvc.perform(put("/api/products/" + product.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Audit Lamp\",\"category\":\"garden\",\"price\":12,\"cost\":5,\"availableQuantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price", is(12)))
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).get("data");
        assertThat(Instant.parse(data.get("updatedAt").asText())).isAfter(before);
        assertThat(data.get("updatedBy").asText()).isEqualTo(data.get("createdBy").asText());
        assertThat(data.get("createdAt").asText()).isEqualTo(product.getCreatedAt().toString());
    }
}
