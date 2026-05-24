package io.github.dfa1.datastore;

import java.time.LocalDate;

public record OhlcRecord(
        LocalDate date,
        Symbol symbol,
        double open,
        double high,
        double low,
        double close,
        long volume
) {
    public OhlcRecord {
        if (open < 0 || high < 0 || low < 0 || close < 0)
            throw new IllegalArgumentException("prices must be non-negative");
        if (high < open || high < close)
            throw new IllegalArgumentException("high must be >= open and close");
        if (low > open || low > close)
            throw new IllegalArgumentException("low must be <= open and close");
        if (volume < 0)
            throw new IllegalArgumentException("volume must be non-negative");
    }
}
