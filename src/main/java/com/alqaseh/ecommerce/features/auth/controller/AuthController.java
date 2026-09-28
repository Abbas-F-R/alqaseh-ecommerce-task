package com.alqaseh.ecommerce.features.auth.controller;

import com.alqaseh.ecommerce.features.auth.dto.request.LoginRequest;
import com.alqaseh.ecommerce.features.auth.service.AuthService;
import com.alqaseh.ecommerce.shared.controller.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user authentication")
public class AuthController extends BaseController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Authenticate a user and issue a JWT")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return toResponseEntity(authService.login(request), http);
    }
}
