package com.envatobank.api.common.util;

import java.math.BigDecimal;

import com.envatobank.api.account.CurrencyCode;

public final class MoneyUtil {

    private MoneyUtil() {
    }

    /** Renders minor units (e.g. 12345) as a decimal string with the correct scale ("123.45"). */
    public static String format(long minorUnits, CurrencyCode currency) {
        return BigDecimal.valueOf(minorUnits, currency.minorUnits()).toPlainString();
    }
}
