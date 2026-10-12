package com.envatobank.api.account;

import java.util.Arrays;

/** ISO 4217 codes supported by the platform. All use 2 decimal minor units. */
public enum CurrencyCode {
    TRY(2),
    USD(2),
    EUR(2);

    private final int minorUnits;

    CurrencyCode(int minorUnits) {
        this.minorUnits = minorUnits;
    }

    public int minorUnits() {
        return minorUnits;
    }

    public static boolean isSupported(String code) {
        return code != null && Arrays.stream(values()).anyMatch(c -> c.name().equalsIgnoreCase(code));
    }
}
