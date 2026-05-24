package io.github.dfa1.datastore;

import java.time.LocalDate;

public record OhlcRecord(
        LocalDate date,
        String symbol,
        double open,
        double high,
        double low,
        double close,
        long volume
) {
    public OhlcRecord {
        if (high < open || high < close)
            throw new IllegalArgumentException("high must be >= open and close");
        if (low > open || low > close)
            throw new IllegalArgumentException("low must be <= open and close");
        if (low < 0 || open < 0)
            throw new IllegalArgumentException("prices must be positive");
        if (volume < 0)
            throw new IllegalArgumentException("volume must be non-negative");
    }
}
