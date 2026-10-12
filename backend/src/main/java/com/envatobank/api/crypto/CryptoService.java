package com.envatobank.api.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.envatobank.api.config.AppSecurityProperties;

/**
 * Field level encryption for PII (AES-256-GCM) and deterministic blind indexes
 * (HMAC-SHA256) that permit equality lookups without decrypting stored data.
 */
@Service
public class CryptoService {

    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int AES_KEY_BYTES = 32;

    private final SecretKeySpec aesKey;
    private final byte[] indexKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public CryptoService(AppSecurityProperties properties) {
        AppSecurityProperties.Encryption cfg = properties.encryption();
        byte[] keyBytes = decode(cfg.key(), "PII_ENCRYPTION_KEY");
        if (keyBytes.length != AES_KEY_BYTES) {
            throw new IllegalStateException("PII_ENCRYPTION_KEY must decode to 32 bytes for AES-256");
        }
        this.aesKey = new SecretKeySpec(keyBytes, "AES");

        byte[] macBytes = decode(cfg.indexKey(), "PII_INDEX_KEY");
        if (macBytes.length < 16) {
            throw new IllegalStateException("PII_INDEX_KEY must decode to at least 16 bytes");
        }
        this.indexKey = macBytes;
    }

    /** Encrypts plaintext and returns Base64(iv || ciphertext+tag). */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv).put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to encrypt field", ex);
        }
    }

    /** Decrypts a value produced by {@link #encrypt(String)}. */
    public String decrypt(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(encoded);
            ByteBuffer buffer = ByteBuffer.wrap(raw);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to decrypt field", ex);
        }
    }

    /** Deterministic, keyed index that allows equality search over encrypted data. */
    public String blindIndex(String value) {
        if (value == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(indexKey, "HmacSHA256"));
            byte[] digest = mac.doFinal(value.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to compute blind index", ex);
        }
    }

    private static byte[] decode(String base64, String name) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(name + " must be valid Base64", ex);
        }
    }
}
