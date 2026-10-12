package com.envatobank.api.account.dto;

import com.envatobank.api.account.AccountType;
import com.envatobank.api.account.CurrencyCode;

import jakarta.validation.constraints.NotNull;

public record CreateAccountRequest(
        @NotNull CurrencyCode currency,
        @NotNull AccountType type
) {
}
