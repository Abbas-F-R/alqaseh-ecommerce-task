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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Input that is too long, too large or malformed is a 400 that names the field: it never reaches a service or the database. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InputBoundsIntegrationTest {

    private static final String ITEM = "{\"productId\":\"01923450-0000-7000-8000-000000000001\",\"quantity\":1}";

    @Autowired
    private MockMvc mockMvc;

    private void assertLoginRejected(String body, String field) throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.validationErrors." + field, notNullValue()));
    }

    @Test
    @DisplayName("Login: a username of 51 characters, a password of 129, and forbidden characters are 400s")
    void login() throws Exception {
        assertLoginRejected("{\"username\":\"" + "a".repeat(51) + "\",\"password\":\"x\"}", "username");
        assertLoginRejected("{\"username\":\"admin\",\"password\":\"" + "p".repeat(129) + "\"}", "password");
        assertLoginRejected("{\"username\":\"ad min\",\"password\":\"x\"}", "username");
        assertLoginRejected("{\"username\":\"admin\\r\\nforged\",\"password\":\"x\"}", "username");
        assertLoginRejected("{\"username\":\"\",\"password\":\"\"}", "username");
    }

    @Test
    @DisplayName("Login: the boundaries themselves are not a 400 (wrong credentials are a 401)")
    void loginBoundaries() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + "a".repeat(50) + "\",\"password\":\"" + "p".repeat(128) + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    private void assertOrderRejected(String payment, String field) throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[" + ITEM + "],\"payment\":" + payment + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors['" + field + "']", notNullValue()));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("Payment: bad card, phone or wallet password, and fields of the other method, are 400s")
    void payment() throws Exception {
        assertOrderRejected("{\"method\":\"CREDIT_CARD\",\"cardNumber\":\"4111\"}", "payment.cardNumber");
        assertOrderRejected("{\"method\":\"CREDIT_CARD\",\"cardNumber\":\"" + "4".repeat(20) + "\"}", "payment.cardNumber");
        assertOrderRejected("{\"method\":\"CREDIT_CARD\",\"cardNumber\":\"abcd1111abcd1111\"}", "payment.cardNumber");
        assertOrderRejected("{\"method\":\"XYZ_WALLET\",\"phoneNumber\":\"12\",\"walletPassword\":\"s\"}", "payment.phoneNumber");
        assertOrderRejected("{\"method\":\"XYZ_WALLET\",\"phoneNumber\":\"+9647800000000\",\"walletPassword\":\"" + "p".repeat(129) + "\"}", "payment.walletPassword");
        assertOrderRejected("{\"method\":\"CREDIT_CARD\",\"cardNumber\":\"4111111111111111\",\"walletPassword\":\"secret\"}", "payment.onlyChosenMethodFields");
        assertOrderRejected("{\"method\":\"XYZ_WALLET\",\"phoneNumber\":\"+9647800000000\",\"walletPassword\":\"s\",\"cardNumber\":\"4111111111111111\"}", "payment.onlyChosenMethodFields");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin lists: a page above 100,000, an int-overflowing page and a filter that is too long are 400s")
    void adminFilters() throws Exception {
        mockMvc.perform(get("/api/admin/products").param("page", "100001")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/products").param("page", "2147483647")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/products").param("name", "a".repeat(151)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.name", notNullValue()));
        mockMvc.perform(get("/api/orders").param("customer", "a".repeat(51)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.customer", notNullValue()));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("Customer list: a cursor of 101 characters and a name of 151 are 400s")
    void customerFilters() throws Exception {
        mockMvc.perform(get("/api/customer/products").param("cursor", "A".repeat(101)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.cursor", notNullValue()));
        mockMvc.perform(get("/api/customer/products").param("name", "a".repeat(151)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.name", notNullValue()));
    }
}
