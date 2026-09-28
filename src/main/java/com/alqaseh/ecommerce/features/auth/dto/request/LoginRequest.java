package com.alqaseh.ecommerce.features.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "User login credentials payload")
public class LoginRequest {

    @NotBlank(message = "Username cannot be blank")
    @Schema(description = "Account username", example = "admin")
    private String username;

    @NotBlank(message = "Password cannot be blank")
    @Schema(description = "Account password", example = "Admin123!")
    private String password;
}
