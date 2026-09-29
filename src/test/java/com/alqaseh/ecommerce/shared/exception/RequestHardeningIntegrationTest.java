package com.alqaseh.ecommerce.shared.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Requests that used to end in a 500, a silent change of the value or the wrong status code (found by a black-box run against
 * PostgreSQL): each is now a clean answer, decided before anything is stored.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestHardeningIntegrationTest {

    private static final String PAYMENT = "\"payment\":{\"method\":\"CREDIT_CARD\",\"cardNumber\":\"4111111111111111\"}";

    @Autowired
    private MockMvc mockMvc;

    private String order(String items) {
        return "{\"items\":" + items + "," + PAYMENT + "}";
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("A quantity of 1.5, a quantity as text, 1e2 and a null order line are 400s (nothing is truncated or coerced)")
    void orderNumbers() throws Exception {
        String id = UUID.randomUUID().toString();
        for (String items : new String[]{
                "[{\"productId\":\"" + id + "\",\"quantity\":1.5}]",
                "[{\"productId\":\"" + id + "\",\"quantity\":\"2\"}]",
                "[{\"productId\":\"" + id + "\",\"quantity\":1e2}]",
                "[null]"}) {
            mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(order(items)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Product: a fractional or textual quantity or price is a 400, and so is a control character in the name")
    void productNumbersAndControlCharacters() throws Exception {
        for (String fields : new String[]{
                "\"price\":10,\"cost\":5,\"availableQuantity\":1.5",
                "\"price\":10,\"cost\":5,\"availableQuantity\":\"5\"",
                "\"price\":\"12.50\",\"cost\":5,\"availableQuantity\":1"}) {
            mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Desk\",\"category\":\"FURNITURE\"," + fields + "}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"nul\\u0000char\",\"category\":\"FURNITURE\",\"price\":10,\"cost\":5,\"availableQuantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.name", notNullValue()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("A NUL character in a name or customer filter is a 400, not a database error")
    void filtersWithControlCharacters() throws Exception {
        mockMvc.perform(get("/api/admin/products").param("name", "a\u0000b"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.name", notNullValue()));
        mockMvc.perform(get("/api/orders").param("customer", "a\u0000b"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.customer", notNullValue()));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("A NUL character in the customer's name filter and in the discount code is a 400")
    void customerInputWithControlCharacters() throws Exception {
        mockMvc.perform(get("/api/customer/products").param("name", "a\u0000b")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}],\"discountCode\":\"AB\\u0000C\"," + PAYMENT + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.discountCode", notNullValue()));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("A customer gets 403 on every admin endpoint even when the request is also invalid (the role is checked before validation)")
    void customerOnAdminEndpoints() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{not json")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/products/not-a-uuid").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/products").param("size", "999")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/orders").param("paymentMethod", "zzz")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("An admin gets 403 on every customer endpoint even when the request is also invalid")
    void adminOnCustomerEndpoints() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/orders/my").param("size", "999")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/customer/products").param("limit", "999")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Without a token every protected endpoint is a 401, for valid and invalid requests alike")
    void anonymous() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/orders/my")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/products").param("size", "999")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("An unknown OpenAPI group is a 404, not a 500")
    void unknownApiDocsGroup() throws Exception {
        mockMvc.perform(get("/v3/api-docs/3-nothing")).andExpect(status().isNotFound());
    }
}
