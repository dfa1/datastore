package io.github.dfa1.datastore;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates synthetic OHLC time series via a geometric random walk.
 * Skips weekends; produces trading-day sequences.
 */
public class OhlcGenerator {

    private static final double DAILY_VOLATILITY = 0.02;  // 2% σ
    private static final double INTRADAY_RANGE_FACTOR = 0.03;

    private final String symbol;
    private final LocalDate startDate;
    private final double initialPrice;
    private final Random random;

    public OhlcGenerator(String symbol, LocalDate startDate, double initialPrice, long seed) {
        this.symbol = symbol;
        this.startDate = startDate;
        this.initialPrice = initialPrice;
        this.random = new Random(seed);
    }

    /** Generates {@code count} trading-day OHLC records. */
    public List<OhlcRecord> generate(int count) {
        var records = new ArrayList<OhlcRecord>(count);
        double prevClose = initialPrice;
        LocalDate date = nextTradingDay(startDate);

        for (int i = 0; i < count; i++) {
            double dailyReturn = random.nextGaussian() * DAILY_VOLATILITY;
            double open        = round(prevClose * (1 + dailyReturn * 0.3));
            double close       = round(prevClose * (1 + dailyReturn));
            double range       = Math.abs(prevClose * random.nextDouble() * INTRADAY_RANGE_FACTOR);
            double high        = round(Math.max(open, close) + range);
            double low         = round(Math.min(open, close) - range);
            long   volume      = Math.max(100_000L, Math.round(1_000_000 + random.nextGaussian() * 200_000));

            records.add(new OhlcRecord(date, symbol, open, high, low, close, volume));

            prevClose = close;
            date = nextTradingDay(date.plusDays(1));
        }

        return records;
    }

    /** Returns a stream-friendly variant for large datasets. */
    public java.util.stream.Stream<OhlcRecord> stream(int count) {
        return generate(count).stream();
    }

    private static LocalDate nextTradingDay(LocalDate date) {
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY
                || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return date;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
