package com.envatobank.api.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.envatobank.api.audit.AuditService;
import com.envatobank.api.auth.dto.LoginRequest;
import com.envatobank.api.auth.dto.RefreshRequest;
import com.envatobank.api.auth.dto.TokenResponse;
import com.envatobank.api.common.error.ApiException;
import com.envatobank.api.common.error.ErrorCode;
import com.envatobank.api.common.util.Hashing;
import com.envatobank.api.config.AppSecurityProperties;
import com.envatobank.api.crypto.CryptoService;
import com.envatobank.api.customer.Customer;
import com.envatobank.api.customer.CustomerRepository;
import com.envatobank.api.customer.CustomerStatus;
import com.envatobank.api.security.AuthenticatedUser;
import com.envatobank.api.security.JwtService;
import com.envatobank.api.security.RefreshToken;
import com.envatobank.api.security.RefreshTokenRepository;

@Service
public class AuthService {

    private final CustomerRepository customers;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CryptoService cryptoService;
    private final AppSecurityProperties properties;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String dummyPasswordHash;

    public AuthService(CustomerRepository customers, RefreshTokenRepository refreshTokens,
            PasswordEncoder passwordEncoder, JwtService jwtService, CryptoService cryptoService,
            AppSecurityProperties properties, AuditService auditService) {
        this.customers = customers;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.cryptoService = cryptoService;
        this.properties = properties;
        this.auditService = auditService;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String emailHash = cryptoService.blindIndex(request.email());
        Customer customer = customers.findByEmailHash(emailHash).orElse(null);

        if (customer == null) {
            // Equalize timing to reduce user-enumeration signal.
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            auditService.record("LOGIN_FAILED", null, "ANONYMOUS", "Customer", null,
                    java.util.Map.of("reason", "unknown_email"));
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
        }

        Instant now = Instant.now();

        if (customer.getStatus() == CustomerStatus.CLOSED) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
        }
        if (customer.getLockedUntil() != null && customer.getLockedUntil().isAfter(now)) {
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED, "Account temporarily locked. Try again later.");
        }
        if (!passwordEncoder.matches(request.password(), customer.getPasswordHash())) {
            registerFailedAttempt(customer, now);
            auditService.record("LOGIN_FAILED", customer.getId(), "CUSTOMER", "Customer",
                    customer.getId().toString(), java.util.Map.of("reason", "bad_password"));
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
        }
        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CUSTOMER_NOT_ACTIVE, "Customer is not active");
        }

        customer.setFailedLoginAttempts(0);
        customer.setLockedUntil(null);
        customer.setUpdatedAt(now);
        customers.save(customer);

        auditService.record("LOGIN_SUCCESS", customer.getId(), customer.getRole().name(), "Customer",
                customer.getId().toString(), null);

        return issueTokens(customer, now);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        String tokenHash = Hashing.sha256Hex(request.refreshToken());
        RefreshToken stored = refreshTokens.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid refresh token"));

        Instant now = Instant.now();
        if (stored.isRevoked()) {
            refreshTokens.revokeAllForCustomer(stored.getCustomerId());
            auditService.record("REFRESH_REUSE_DETECTED", stored.getCustomerId(), "CUSTOMER", "Customer",
                    stored.getCustomerId().toString(), null);
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Refresh token reuse detected; all sessions revoked");
        }
        if (stored.getExpiresAt().isBefore(now)) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Refresh token expired");
        }

        Customer customer = customers.findById(stored.getCustomerId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid refresh token"));
        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CUSTOMER_NOT_ACTIVE, "Customer is not active");
        }

        stored.setRevoked(true);
        refreshTokens.save(stored);
        return issueTokens(customer, now);
    }

    @Transactional
    public void logout(UUID customerId) {
        refreshTokens.revokeAllForCustomer(customerId);
        auditService.record("LOGOUT", customerId, "CUSTOMER", "Customer", customerId.toString(), null);
    }

    private void registerFailedAttempt(Customer customer, Instant now) {
        int attempts = customer.getFailedLoginAttempts() + 1;
        customer.setFailedLoginAttempts(attempts);
        int max = properties.login().maxFailedAttempts();
        if (attempts >= max) {
            customer.setLockedUntil(now.plusSeconds(properties.login().lockMinutes() * 60L));
            customer.setFailedLoginAttempts(0);
        }
        customer.setUpdatedAt(now);
        customers.save(customer);
    }

    private TokenResponse issueTokens(Customer customer, Instant now) {
        String email = cryptoService.decrypt(customer.getEmailEncrypted());
        AuthenticatedUser principal = new AuthenticatedUser(customer.getId(), email, customer.getRole().name());
        String accessToken = jwtService.generateAccessToken(principal);

        byte[] random = new byte[48];
        secureRandom.nextBytes(random);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);

        RefreshToken entity = new RefreshToken();
        entity.setCustomerId(customer.getId());
        entity.setTokenHash(Hashing.sha256Hex(refreshToken));
        entity.setExpiresAt(now.plusSeconds(jwtService.refreshTokenTtlSeconds()));
        entity.setRevoked(false);
        entity.setCreatedAt(now);
        refreshTokens.save(entity);

        return new TokenResponse(accessToken, refreshToken, "Bearer", jwtService.accessTokenTtlSeconds(),
                customer.getRole().name(), customer.getId(),
                customer.getFirstName() + " " + customer.getLastName());
    }
}
