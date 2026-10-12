package com.envatobank.api.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.envatobank.api.config.AppSecurityProperties;

class CryptoServiceTest {

    private static CryptoService newService() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        String indexKey = Base64.getEncoder().encodeToString(new byte[32]);
        AppSecurityProperties properties = new AppSecurityProperties(
                null,
                new AppSecurityProperties.Encryption(key, indexKey),
                null,
                null);
        return new CryptoService(properties);
    }

    @Test
    void encryptThenDecryptRoundTrips() {
        CryptoService crypto = newService();
        String plaintext = "ayse@example.com";

        String encrypted = crypto.encrypt(plaintext);

        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(crypto.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test
    void encryptionUsesRandomIvSoCiphertextDiffers() {
        CryptoService crypto = newService();

        String first = crypto.encrypt("same-value");
        String second = crypto.encrypt("same-value");

        assertThat(first).isNotEqualTo(second);
        assertThat(crypto.decrypt(first)).isEqualTo(crypto.decrypt(second));
    }

    @Test
    void blindIndexIsDeterministicAndCaseInsensitive() {
        CryptoService crypto = newService();

        String lower = crypto.blindIndex("ayse@example.com");
        String upper = crypto.blindIndex("  AYSE@Example.com ");

        assertThat(lower).hasSize(64);
        assertThat(lower).isEqualTo(upper);
    }

    @Test
    void blindIndexDiffersForDifferentValues() {
        CryptoService crypto = newService();

        assertThat(crypto.blindIndex("a@example.com"))
                .isNotEqualTo(crypto.blindIndex("b@example.com"));
    }
}
