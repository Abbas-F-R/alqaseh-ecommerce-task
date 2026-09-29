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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bad input must be answered with a 4xx and a consistent error body BEFORE any service runs
 * (Bean Validation / Spring MVC binding), never with a 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestValidationIntegrationTest {

    private static final String VALID_PRODUCT = """
            {"name":"Desk","category":"FURNITURE","price":100.00,"cost":60.00,"availableQuantity":5}""";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Empty product payload: 400 VALIDATION_ERROR listing every invalid field")
    void emptyProductPayload() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.path", is("/api/products")))
                .andExpect(jsonPath("$.validationErrors.name", notNullValue()))
                .andExpect(jsonPath("$.validationErrors.category", notNullValue()))
                .andExpect(jsonPath("$.validationErrors.price", notNullValue()))
                .andExpect(jsonPath("$.validationErrors.cost", notNullValue()))
                .andExpect(jsonPath("$.validationErrors.availableQuantity", notNullValue()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Negative price, negative stock and blank name are rejected")
    void invalidProductValues() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"   ","category":"FURNITURE","price":-1,"cost":60,"availableQuantity":-5}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.name", notNullValue()))
                .andExpect(jsonPath("$.validationErrors.price", notNullValue()))
                .andExpect(jsonPath("$.validationErrors.availableQuantity", notNullValue()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Values that would overflow the database columns are rejected as 400, not 500")
    void oversizedValues() throws Exception {
        String longName = "x".repeat(151);
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"%s","category":"FURNITURE","price":12345678901.00,"cost":60,"availableQuantity":5}"""
                        .formatted(longName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.name", notNullValue()))
                .andExpect(jsonPath("$.validationErrors.price", notNullValue()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Malformed JSON: 400 BAD_REQUEST")
    void malformedJson() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Unknown enum value (category) : 400 BAD_REQUEST")
    void unknownEnumValue() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(
                        VALID_PRODUCT.replace("FURNITURE", "TOYS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Malformed UUID in the path: 400 BAD_REQUEST")
    void malformedUuid() throws Exception {
        mockMvc.perform(put("/api/products/not-a-uuid").contentType(MediaType.APPLICATION_JSON).content(VALID_PRODUCT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("Limit above the maximum, invalid cursor, or bad page: 400 with the offending fields")
    void invalidPagination() throws Exception {
        mockMvc.perform(get("/api/customer/products").param("limit", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.validationErrors.limit", notNullValue()));
        mockMvc.perform(get("/api/customer/products").param("cursor", "bad-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.validationErrors.cursor", notNullValue()));
        mockMvc.perform(get("/api/orders/my").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("Unknown category filter value: 400, not 500")
    void unknownCategoryFilter() throws Exception {
        mockMvc.perform(get("/api/customer/products").param("category", "TOYS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.validationErrors.category", is("Invalid value")))
                .andExpect(content().string(not(containsString("java."))))
                .andExpect(content().string(not(containsString("com.alqaseh"))));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("Order without items, or with quantity 0, is rejected before the service runs")
    void invalidOrders() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"items":[],"payment":{"method":"CREDIT_CARD","cardNumber":"4111"}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.items", notNullValue()));

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"items":[{"productId":"01923450-0000-7000-8000-000000000001","quantity":0}],
                         "payment":{"method":"CREDIT_CARD","cardNumber":"4111"}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors", notNullValue()));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("A line quantity above 10,000 is a 400 (two such lines of one product used to overflow the merged int quantity and end as a 500)")
    void quantityIsBounded() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"items":[{"productId":"01923450-0000-7000-8000-000000000001","quantity":10001}],
                         "payment":{"method":"CREDIT_CARD","cardNumber":"4111"}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors['items[0].quantity']", notNullValue()));

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"items":[{"productId":"01923450-0000-7000-8000-000000000001","quantity":2000000000},
                                  {"productId":"01923450-0000-7000-8000-000000000001","quantity":2000000000}],
                         "payment":{"method":"CREDIT_CARD","cardNumber":"4111"}}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("Payment fields required by the chosen method are validated (card number for CreditCard)")
    void paymentFieldsRequiredByMethod() throws Exception {
        String item = "\"items\":[{\"productId\":\"01923450-0000-7000-8000-000000000001\",\"quantity\":1}]";

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{" + item + ",\"payment\":{\"method\":\"CREDIT_CARD\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors['payment.cardNumberProvided']", notNullValue()));

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{" + item + ",\"payment\":{\"method\":\"XYZ_WALLET\",\"phoneNumber\":\"+9647\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors['payment.walletCredentialsProvided']", notNullValue()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Unknown route: 404, and unsupported method: 405 (Spring MVC statuses are preserved, not turned into 500)")
    void mvcStatusesArePreserved() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
        mockMvc.perform(patch("/api/products"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code", is("METHOD_NOT_ALLOWED")));
        mockMvc.perform(post("/api/products").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code", is("UNSUPPORTED_MEDIA_TYPE")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Error messages follow Accept-Language, and validation details never leak internals")
    void localizedAndSafe() throws Exception {
        mockMvc.perform(post("/api/products").header("Accept-Language", "ar")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("فشل التحقق")))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("com.alqaseh"))));
    }
}
