package com.envatobank.api.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.envatobank.api.account.CurrencyCode;

class MoneyUtilTest {

    @Test
    void formatsMinorUnitsWithTwoDecimals() {
        assertThat(MoneyUtil.format(12345, CurrencyCode.TRY)).isEqualTo("123.45");
        assertThat(MoneyUtil.format(100000, CurrencyCode.USD)).isEqualTo("1000.00");
        assertThat(MoneyUtil.format(0, CurrencyCode.EUR)).isEqualTo("0.00");
        assertThat(MoneyUtil.format(5, CurrencyCode.TRY)).isEqualTo("0.05");
    }

    @Test
    void sha256IsStableAndHexEncoded() {
        String hash = Hashing.sha256Hex("envato");

        assertThat(hash).hasSize(64);
        assertThat(hash).isEqualTo(Hashing.sha256Hex("envato"));
        assertThat(hash).isNotEqualTo(Hashing.sha256Hex("envato2"));
    }
}
