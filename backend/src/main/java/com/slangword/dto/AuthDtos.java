package com.slangword.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 64) String username,
            @NotBlank @Size(min = 6, max = 128) String password) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    /**
     * @param accessToken       short-lived JWT sent with every request
     * @param refreshToken      long-lived, revocable, exchanged at /auth/refresh
     * @param expiresInSeconds  lifetime of the access token, so the client can
     *                          refresh ahead of expiry instead of waiting for a 401
     */
    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String username,
            String role,
            long expiresInSeconds) {
    }
}
