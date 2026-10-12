package com.envatobank.api.auth.dto;

import java.util.UUID;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        String role,
        UUID customerId,
        String fullName
) {
}
