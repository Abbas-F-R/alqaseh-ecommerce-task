package com.alqaseh.ecommerce.infrastructure.security;

import com.alqaseh.ecommerce.features.auth.service.CustomUserDetailsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
    private final HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService, resolver);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest bearer() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        request.addHeader("Authorization", "Bearer some.jwt.token");
        return request;
    }

    @Test
    @DisplayName("A database failure while loading the user is handed to the exception resolver (500), not turned into 401")
    void infrastructureFailureIsNotAnAuthenticationFailure() throws Exception {
        var failure = new DataAccessResourceFailureException("db down");
        when(jwtService.extractUsername("some.jwt.token")).thenReturn("admin");
        when(userDetailsService.loadUserByUsername("admin")).thenThrow(failure);
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(bearer(), response, chain);

        verify(resolver).resolveException(any(), any(), isNull(), eq(failure));
        assertThat(chain.getRequest()).as("the request must not continue down the chain").isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("An invalid token leaves the request unauthenticated and continues (the entry point answers 401)")
    void invalidTokenContinuesUnauthenticated() throws Exception {
        when(jwtService.extractUsername("some.jwt.token")).thenThrow(new io.jsonwebtoken.MalformedJwtException("bad"));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(bearer(), new MockHttpServletResponse(), chain);

        verifyNoInteractions(resolver);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
