package com.alqaseh.ecommerce.features.auth.service;

import com.alqaseh.ecommerce.features.auth.dto.request.LoginRequest;
import com.alqaseh.ecommerce.features.auth.dto.response.LoginResponse;
import com.alqaseh.ecommerce.infrastructure.security.JwtService;
import com.alqaseh.ecommerce.infrastructure.security.UserPrincipal;
import com.alqaseh.ecommerce.shared.error.ErrorCode;
import com.alqaseh.ecommerce.shared.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

/**
 * Only wrong credentials are a business outcome (401). Anything else thrown while authenticating, such as a database outage
 * ({@code InternalAuthenticationServiceException}), is a server problem and propagates to the 500 handler.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Override
    public Result<LoginResponse> login(LoginRequest request) {
        try {
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

            log.info("User logged in: username={}, role={}", principal.getUsername(), principal.getRole());
            return Result.success(LoginResponse.builder()
                    .token(jwtService.generateToken(principal))
                    .username(principal.getUsername())
                    .role(principal.getRole().name())
                    .build());
        } catch (BadCredentialsException e) {
            log.warn("Login failed: invalid credentials for username={}", request.getUsername());
            return Result.failure(ErrorCode.INVALID_CREDENTIALS);
        }
    }
}
