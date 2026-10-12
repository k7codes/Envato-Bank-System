package com.envatobank.api.transfer.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TransferRequest(
        @NotNull UUID sourceAccountId,
        @NotBlank @Size(max = 26) String targetAccountNo,
        @Positive long amountMinor,
        @NotBlank @Size(max = 80) String idempotencyKey,
        @Size(max = 255) String description
) {
}
