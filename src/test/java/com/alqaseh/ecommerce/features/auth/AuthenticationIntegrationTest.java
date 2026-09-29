package com.alqaseh.ecommerce.features.auth;

import com.alqaseh.ecommerce.features.auth.dto.request.LoginRequest;
import com.alqaseh.ecommerce.infrastructure.security.JwtProperties;
import com.alqaseh.ecommerce.infrastructure.security.JwtService;
import com.alqaseh.ecommerce.infrastructure.user.entity.Role;
import com.alqaseh.ecommerce.infrastructure.user.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Login and JWT handling through the real security filter chain (no mocked authentication). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JwtProperties jwtProperties;

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).get("data");
        return data.get("token").asText();
    }

    @Test
    @DisplayName("Valid login returns a token that authenticates later requests")
    void loginThenUseToken() throws Exception {
        String token = login("admin", "Admin123!");

        mockMvc.perform(get("/api/admin/products").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Login response contains token, role and username but never the password hash")
    void loginResponseShape() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"customer1\",\"password\":\"Customer123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role", is("CUSTOMER")))
                .andExpect(jsonPath("$.data.username", is("customer1")))
                .andExpect(content().string(not(containsString("$2a$"))));
    }

    @Test
    @DisplayName("Wrong password and unknown user get the same 401 INVALID_CREDENTIALS (no user enumeration)")
    void invalidCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("INVALID_CREDENTIALS")));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ghost\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("INVALID_CREDENTIALS")));
    }

    @Test
    @DisplayName("Login with missing fields is a 400 validation error")
    void loginValidation() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("No token, garbage token, tampered token and expired token are all 401 UNAUTHORIZED")
    void invalidTokensAreUnauthorized() throws Exception {
        String valid = login("admin", "Admin123!");
        String tampered = valid.substring(0, valid.length() - 4) + "AAAA";
        String expired = Jwts.builder().subject("admin")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secretKey()))).compact();
        String signedWithOtherKey = Jwts.builder().subject("admin")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode("dGhpcy1pcy1hLWRpZmZlcmVudC0yNTYtYml0LXNlY3JldC1rZXk="))).compact();

        mockMvc.perform(get("/api/customer/products")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
        for (String bad : new String[]{"garbage", tampered, expired, signedWithOtherKey}) {
            mockMvc.perform(get("/api/customer/products").header("Authorization", "Bearer " + bad))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
        }
    }

    @Test
    @DisplayName("A valid token for a user that no longer exists is rejected")
    void tokenOfUnknownUserIsRejected() throws Exception {
        User ghost = User.builder().username("ghost").password("x").role(Role.CUSTOMER).build();
        String token = jwtService.generateToken(com.alqaseh.ecommerce.infrastructure.security.UserPrincipal.create(ghost));

        mockMvc.perform(get("/api/customer/products").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Roles are enforced: a customer token cannot create products (403 FORBIDDEN)")
    void customerCannotCreateProduct() throws Exception {
        String token = login("customer1", "Customer123!");

        mockMvc.perform(post("/api/products").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"category\":\"GARDEN\",\"price\":1,\"cost\":1,\"availableQuantity\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("Swagger stays reachable without a token in test/dev configuration; API endpoints do not")
    void publicAndProtectedPaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/api/orders/my")).andExpect(status().isUnauthorized());
    }
}
