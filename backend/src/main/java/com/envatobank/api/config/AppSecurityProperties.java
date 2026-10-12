package com.envatobank.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record AppSecurityProperties(
        Jwt jwt,
        Encryption encryption,
        Argon2 argon2,
        Login login
) {
    public record Jwt(String issuer, long accessTokenTtlSeconds, long refreshTokenTtlSeconds, String secret) {
    }

    public record Encryption(String key, String indexKey) {
    }

    public record Argon2(int saltLength, int hashLength, int parallelism, int memoryInKib, int iterations) {
    }

    public record Login(int maxFailedAttempts, int lockMinutes) {
    }
}
