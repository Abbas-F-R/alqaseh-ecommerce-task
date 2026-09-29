package com.alqaseh.ecommerce.features.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authentication response payload containing JWT access token")
public class LoginResponse {

    @Schema(description = "JWT Bearer access token")
    private String token;

    @Schema(description = "Username of authenticated user", example = "admin")
    private String username;

    @Schema(description = "User role", example = "ADMIN")
    private String role;
}
