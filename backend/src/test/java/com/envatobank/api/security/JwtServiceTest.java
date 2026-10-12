package com.envatobank.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.envatobank.api.config.AppSecurityProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;

class JwtServiceTest {

    private static JwtService newService() {
        String secret = Base64.getEncoder().encodeToString(new byte[48]);
        AppSecurityProperties properties = new AppSecurityProperties(
                new AppSecurityProperties.Jwt("envato-bank-test", 900, 2592000, secret),
                null,
                null,
                null);
        return new JwtService(properties);
    }

    @Test
    void generatesAndParsesAccessToken() {
        JwtService service = newService();
        UUID id = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(id, "ayse@example.com", "CUSTOMER");

        String token = service.generateAccessToken(user);
        Jws<Claims> parsed = service.parse(token);

        assertThat(parsed.getPayload().getSubject()).isEqualTo(id.toString());
        assertThat(parsed.getPayload().get("role", String.class)).isEqualTo("CUSTOMER");
        assertThat(parsed.getPayload().get("email", String.class)).isEqualTo("ayse@example.com");
        assertThat(parsed.getPayload().getIssuer()).isEqualTo("envato-bank-test");
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        JwtService service = newService();
        String foreignSecret = Base64.getEncoder().encodeToString(new byte[48]);
        byte[] altered = Base64.getDecoder().decode(foreignSecret);
        altered[0] ^= 0xFF;
        String alteredSecret = Base64.getEncoder().encodeToString(altered);
        JwtService other = new JwtService(new AppSecurityProperties(
                new AppSecurityProperties.Jwt("envato-bank-test", 900, 2592000, alteredSecret),
                null, null, null));

        String token = other.generateAccessToken(new AuthenticatedUser(UUID.randomUUID(), "x@y.z", "CUSTOMER"));

        assertThatThrownBy(() -> service.parse(token))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }
}
