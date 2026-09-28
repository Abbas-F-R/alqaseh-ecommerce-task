package com.alqaseh.ecommerce.features.auth.service;

import com.alqaseh.ecommerce.features.auth.dto.request.LoginRequest;
import com.alqaseh.ecommerce.features.auth.dto.response.LoginResponse;
import com.alqaseh.ecommerce.infrastructure.security.JwtService;
import com.alqaseh.ecommerce.infrastructure.security.UserPrincipal;
import com.alqaseh.ecommerce.infrastructure.user.entity.Role;
import com.alqaseh.ecommerce.shared.error.ErrorCode;
import com.alqaseh.ecommerce.shared.result.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    @DisplayName("Valid credentials return Result.success with JWT token and role")
    void shouldLoginSuccessfully() {
        LoginRequest request = new LoginRequest("admin", "Admin123!");
        UUID userId = UUID.fromString("01923450-0000-7000-8000-000000000001");
        UserPrincipal principal = new UserPrincipal(
                userId,
                "admin",
                "hash",
                Role.ADMIN,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(jwtService.generateToken(principal)).thenReturn("mock.jwt.token");

        Result<LoginResponse> result = authService.login(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue().getToken()).isEqualTo("mock.jwt.token");
        assertThat(result.getValue().getUsername()).isEqualTo("admin");
        assertThat(result.getValue().getRole()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Invalid credentials return Result.failure with INVALID_CREDENTIALS")
    void shouldReturnFailureWhenCredentialsInvalid() {
        LoginRequest request = new LoginRequest("admin", "wrongPass");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        Result<LoginResponse> result = authService.login(request);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }


    @Test
    @DisplayName("An infrastructure failure while authenticating (e.g. database down) is not reported as bad credentials")
    void infrastructureFailurePropagates() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new org.springframework.security.authentication.InternalAuthenticationServiceException("db down"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "Admin123!")))
                .isInstanceOf(org.springframework.security.authentication.InternalAuthenticationServiceException.class);
    }
}
