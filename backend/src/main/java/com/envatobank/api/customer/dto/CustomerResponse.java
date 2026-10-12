package com.envatobank.api.customer.dto;

import java.time.Instant;
import java.util.UUID;

import com.envatobank.api.customer.CustomerStatus;
import com.envatobank.api.customer.Role;

public record CustomerResponse(
        UUID id,
        String customerNo,
        String firstName,
        String lastName,
        String email,
        String phone,
        String nationalIdMasked,
        Role role,
        CustomerStatus status,
        Instant createdAt
) {
}
