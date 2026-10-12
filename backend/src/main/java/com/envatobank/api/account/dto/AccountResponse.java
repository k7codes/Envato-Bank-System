package com.envatobank.api.account.dto;

import java.time.Instant;
import java.util.UUID;

import com.envatobank.api.account.AccountStatus;
import com.envatobank.api.account.AccountType;
import com.envatobank.api.account.CurrencyCode;

public record AccountResponse(
        UUID id,
        String accountNo,
        UUID customerId,
        CurrencyCode currency,
        AccountType type,
        AccountStatus status,
        long balanceMinor,
        String balance,
        Instant createdAt
) {
}
