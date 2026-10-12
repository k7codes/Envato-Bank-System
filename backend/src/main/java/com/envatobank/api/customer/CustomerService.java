package com.envatobank.api.customer;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.envatobank.api.audit.AuditService;
import com.envatobank.api.common.error.ApiException;
import com.envatobank.api.common.error.ErrorCode;
import com.envatobank.api.crypto.CryptoService;
import com.envatobank.api.customer.dto.CreateCustomerRequest;
import com.envatobank.api.customer.dto.CustomerResponse;
import com.envatobank.api.security.AuthenticatedUser;

@Service
public class CustomerService {

    private final CustomerRepository customers;
    private final CryptoService cryptoService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public CustomerService(CustomerRepository customers, CryptoService cryptoService,
            PasswordEncoder passwordEncoder, AuditService auditService) {
        this.customers = customers;
        this.cryptoService = cryptoService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request, AuthenticatedUser actor) {
        Role effectiveRole = request.role() == null ? Role.CUSTOMER : request.role();
        if (effectiveRole != Role.CUSTOMER && !Role.ADMIN.name().equals(actor.role())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Only an administrator can create non-customer users");
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String emailHash = cryptoService.blindIndex(email);
        String nationalIdHash = cryptoService.blindIndex(request.nationalId());

        if (customers.existsByEmailHash(emailHash)) {
            throw new ApiException(ErrorCode.CONFLICT, "Email is already registered");
        }
        if (customers.existsByNationalIdHash(nationalIdHash)) {
            throw new ApiException(ErrorCode.CONFLICT, "National id is already registered");
        }

        Instant now = Instant.now();
        Customer customer = new Customer();
        customer.setCustomerNo(generateCustomerNo());
        customer.setFirstName(request.firstName().trim());
        customer.setLastName(request.lastName().trim());
        customer.setEmailEncrypted(cryptoService.encrypt(email));
        customer.setEmailHash(emailHash);
        customer.setNationalIdEncrypted(cryptoService.encrypt(request.nationalId()));
        customer.setNationalIdHash(nationalIdHash);
        customer.setPhoneEncrypted(cryptoService.encrypt(request.phone().trim()));
        customer.setPasswordHash(passwordEncoder.encode(request.password()));
        customer.setRole(effectiveRole);
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.setFailedLoginAttempts(0);
        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);
        customers.save(customer);

        auditService.record("CUSTOMER_CREATED", actor.id(), actor.role(), "Customer",
                customer.getId().toString(),
                Map.of("customerNo", customer.getCustomerNo(), "role", effectiveRole.name()));

        return toResponse(customer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(UUID id, AuthenticatedUser actor) {
        assertCanRead(id, actor);
        Customer customer = customers.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Customer not found"));
        return toResponse(customer);
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> list(Pageable pageable, AuthenticatedUser actor) {
        if (Role.CUSTOMER.name().equals(actor.role())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Not permitted to list customers");
        }
        return customers.findAll(pageable).map(this::toResponse);
    }

    private void assertCanRead(UUID customerId, AuthenticatedUser actor) {
        boolean self = Role.CUSTOMER.name().equals(actor.role()) && actor.id().equals(customerId);
        if (!self && Role.CUSTOMER.name().equals(actor.role())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Not permitted to read this customer");
        }
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getCustomerNo(),
                customer.getFirstName(),
                customer.getLastName(),
                cryptoService.decrypt(customer.getEmailEncrypted()),
                cryptoService.decrypt(customer.getPhoneEncrypted()),
                mask(cryptoService.decrypt(customer.getNationalIdEncrypted())),
                customer.getRole(),
                customer.getStatus(),
                customer.getCreatedAt());
    }

    private static String mask(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        return "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
    }

    private String generateCustomerNo() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = "C" + String.format("%010d", ThreadLocalRandom.current().nextLong(0, 10_000_000_000L));
            if (!customers.existsByCustomerNo(candidate)) {
                return candidate;
            }
        }
        throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not allocate a customer number");
    }
}
