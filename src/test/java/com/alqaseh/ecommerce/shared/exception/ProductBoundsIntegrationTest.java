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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Product input that breaks a rule is a 400 that names the field: it never reaches the service or the database. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductBoundsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Product: a price of 0, a cost above the price, a stock above 1,000,000 and a third decimal are 400s")
    void product() throws Exception {
        for (String[] bad : new String[][]{
                {"\"price\":0,\"cost\":0,\"availableQuantity\":1", "price"},
                {"\"price\":10,\"cost\":5,\"availableQuantity\":1000001", "availableQuantity"},
                {"\"price\":10.005,\"cost\":5,\"availableQuantity\":1", "price"},
                {"\"price\":10,\"cost\":5.555,\"availableQuantity\":1", "cost"},
                {"\"price\":10,\"cost\":10.01,\"availableQuantity\":1", "costNotAbovePrice"}}) {
            String body = "{\"name\":\"Desk\",\"category\":\"FURNITURE\"," + bad[0] + "}";
            mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.validationErrors." + bad[1], notNullValue()));
            // the replace (PUT) takes the same payload and the same rules
            mockMvc.perform(put("/api/products/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.validationErrors." + bad[1], notNullValue()));
        }
    }
}
