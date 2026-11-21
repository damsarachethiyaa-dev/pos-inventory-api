package com.chethiya.posapi.dto;

import com.chethiya.posapi.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Username is required")
            String username,
            @NotBlank(message = "Password is required")
            String password) {
    }

    public record RegisterRequest(
            @NotBlank(message = "Username is required")
            @Size(min = 3, max = 60, message = "Username must be between 3 and 60 characters")
            String username,
            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
            String password,
            @NotBlank(message = "Full name is required")
            @Size(max = 120)
            String fullName,
            @NotNull(message = "Role is required")
            Role role) {
    }

    public record AuthResponse(
            String accessToken,
            String tokenType,
            long expiresInSeconds,
            String username,
            Role role) {
    }
}
