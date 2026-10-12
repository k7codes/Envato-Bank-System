package com.envatobank.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.transfer")
public record TransferProperties(long perTransactionLimitMinor, long dailyLimitMinor) {
}
