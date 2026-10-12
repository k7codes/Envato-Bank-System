package com.envatobank.api.security;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.envatobank.api.config.AppSecurityProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final String issuer;
    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlSeconds;

    public JwtService(AppSecurityProperties properties) {
        AppSecurityProperties.Jwt jwt = properties.jwt();
        byte[] secret = Base64.getDecoder().decode(jwt.secret());
        if (secret.length < 32) {
            throw new IllegalStateException("JWT_SECRET must decode to at least 32 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret);
        this.issuer = jwt.issuer();
        this.accessTokenTtlSeconds = jwt.accessTokenTtlSeconds();
        this.refreshTokenTtlSeconds = jwt.refreshTokenTtlSeconds();
    }

    public String generateAccessToken(AuthenticatedUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.id().toString())
                .claim("email", user.email())
                .claim("role", user.role())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenTtlSeconds)))
                .signWith(signingKey)
                .compact();
    }

    public Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token);
    }

    public long accessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public long refreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }
}
