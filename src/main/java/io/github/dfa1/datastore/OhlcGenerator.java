package io.github.dfa1.datastore;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.stream.Stream;

public class OhlcGenerator {

    private static final double DAILY_VOLATILITY      = 0.02;
    private static final double INTRADAY_RANGE_FACTOR = 0.03;

    private final String    symbol;
    private final LocalDate startDate;
    private final double    initialPrice;
    private final long      seed;

    public OhlcGenerator(String symbol, LocalDate startDate, double initialPrice, long seed) {
        this.symbol       = symbol;
        this.startDate    = startDate;
        this.initialPrice = initialPrice;
        this.seed         = seed;
    }

    /** Lazy stream of {@code count} trading-day records. Sequential only — shared mutable state in lambda capture. */
    public Stream<OhlcRecord> stream(int count) {
        var random     = new java.util.Random(seed);
        double[] prev  = {initialPrice};
        LocalDate[] dt = {nextTradingDay(startDate)};

        return Stream.generate(() -> {
            double dailyReturn = random.nextGaussian() * DAILY_VOLATILITY;
            double open        = round(prev[0] * (1 + dailyReturn * 0.3));
            double close       = round(prev[0] * (1 + dailyReturn));
            double range       = Math.abs(prev[0] * random.nextDouble() * INTRADAY_RANGE_FACTOR);
            double high        = round(Math.max(open, close) + range);
            double low         = round(Math.min(open, close) - range);
            long   volume      = Math.max(100_000L, Math.round(1_000_000 + random.nextGaussian() * 200_000));

            var rec = new OhlcRecord(dt[0], symbol, open, high, low, close, volume);
            prev[0] = close;
            dt[0]   = nextTradingDay(dt[0].plusDays(1));
            return rec;
        }).limit(count);
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
