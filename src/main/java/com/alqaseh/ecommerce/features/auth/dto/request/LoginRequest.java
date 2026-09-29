package com.alqaseh.ecommerce.features.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    /** Letters, digits and . _ @ - only: no control characters can reach the log or the database. */
    @NotBlank(message = "Username cannot be blank")
    @Size(max = 50, message = "Username cannot exceed 50 characters")
    @Pattern(regexp = "[A-Za-z0-9._@-]*", message = "Username may contain only letters, digits and . _ @ -")
    @Schema(description = "Account username", example = "admin")
    private String username;

    @NotBlank(message = "Password cannot be blank")
    @Size(max = 128, message = "Password cannot exceed 128 characters")
    @Schema(description = "Account password", example = "Admin123!")
    private String password;
}
