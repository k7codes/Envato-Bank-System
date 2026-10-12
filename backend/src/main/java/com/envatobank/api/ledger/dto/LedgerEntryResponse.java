package com.envatobank.api.ledger.dto;

import java.time.Instant;
import java.util.UUID;

import com.envatobank.api.account.CurrencyCode;
import com.envatobank.api.ledger.LedgerEntryType;

public record LedgerEntryResponse(
        Instant occurredAt,
        LedgerEntryType type,
        long amountMinor,
        CurrencyCode currency,
        long balanceAfterMinor,
        String description,
        UUID transferId,
        UUID transactionId
) {
}
