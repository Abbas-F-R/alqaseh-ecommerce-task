package com.alqaseh.ecommerce.shared.exception;

import com.alqaseh.ecommerce.features.product.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An unexpected failure (bug, database outage) is HTTP 500 with a safe, generic body.
 * The default (test/prod) configuration must not leak the exception, SQL, hosts or stack traces.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UnexpectedErrorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Infrastructure failure: 500 INTERNAL_SERVER_ERROR without any internal detail")
    void databaseFailureIsAGenericServerError() throws Exception {
        when(productService.listProducts(any())).thenThrow(new DataAccessResourceFailureException(
                "Connection to jdbc:postgresql://db.internal:5432/shop refused, password=hunter2"));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status", is(500)))
                .andExpect(jsonPath("$.code", is("INTERNAL_SERVER_ERROR")))
                .andExpect(jsonPath("$.path", is("/api/products")))
                .andExpect(jsonPath("$.debug").doesNotExist())
                .andExpect(jsonPath("$.validationErrors").doesNotExist())
                .andExpect(content().string(not(containsString("hunter2"))))
                .andExpect(content().string(not(containsString("jdbc"))))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("com.alqaseh"))))
                .andExpect(content().string(not(containsString("at "))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("A programming bug (NullPointerException) is also a plain 500")
    void programmingBugIsAGenericServerError() throws Exception {
        when(productService.listProducts(any())).thenThrow(new NullPointerException("internal state"));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code", is("INTERNAL_SERVER_ERROR")))
                .andExpect(content().string(not(containsString("internal state"))));
    }
}
