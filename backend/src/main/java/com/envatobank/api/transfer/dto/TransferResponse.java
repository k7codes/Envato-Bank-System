package com.envatobank.api.transfer.dto;

import java.time.Instant;
import java.util.UUID;

import com.envatobank.api.account.CurrencyCode;
import com.envatobank.api.transfer.TransferStatus;

public record TransferResponse(
        UUID id,
        String reference,
        TransferStatus status,
        UUID sourceAccountId,
        UUID targetAccountId,
        long amountMinor,
        CurrencyCode currency,
        String description,
        Instant createdAt,
        Instant completedAt
) {
}
