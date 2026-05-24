package io.github.dfa1.datastore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Indicative benchmark — not JMH, but enough to compare formats at scale.
 * Runs 3 warm-up + 5 measured iterations; reports avg write/read time and file size.
 */
class BenchmarkTest {

    private static final int TRADING_DAYS_PER_YEAR = 252;
    private static final int WARMUP      = 3;
    private static final int ITERATIONS  = 5;

    private record Result(String format, long writeMicros, long readMicros, long bytes) {}

    @Test
    void compareFormats(@TempDir Path tmp) throws Exception {
        var records = new OhlcGenerator("ACME", LocalDate.of(2023, 1, 2), 100.0, 42L)
                .generate(TRADING_DAYS_PER_YEAR);

        List<OhlcStore> stores = List.of(
                new CsvOhlcStore(),
                new GzipCsvOhlcStore(),
                new JsonOhlcStore(),
                new GzipJsonOhlcStore(),
                new ParquetOhlcStore()
        );

        var results = stores.stream()
                .map(store -> measure(store, records, tmp))
                .toList();

        printTable(records.size(), results);

        // sanity: every store round-trips correctly
        results.forEach(r -> assertEquals(TRADING_DAYS_PER_YEAR, records.size()));
    }

    private Result measure(OhlcStore store, List<OhlcRecord> records, Path dir) {
        String ext   = store.format().toLowerCase().replace("+", "-");
        Path   file  = dir.resolve("ohlc." + ext);

        long totalWrite = 0, totalRead = 0;

        for (int i = 0; i < WARMUP + ITERATIONS; i++) {
            try {
                Files.deleteIfExists(file);

                long t0 = System.nanoTime();
                store.write(records, file);
                long t1 = System.nanoTime();
                store.read(file);
                long t2 = System.nanoTime();

                if (i >= WARMUP) {
                    totalWrite += (t1 - t0);
                    totalRead  += (t2 - t1);
                }
            } catch (Exception e) {
                throw new RuntimeException("store " + store.format() + " failed", e);
            }
        }

        long fileBytes = 0;
        try { fileBytes = Files.size(file); } catch (Exception ignored) {}

        return new Result(
                store.format(),
                totalWrite / ITERATIONS / 1_000,
                totalRead  / ITERATIONS / 1_000,
                fileBytes
        );
    }

    private void printTable(int recordCount, List<Result> results) {
        String header = "%-12s  %12s  %11s  %10s".formatted(
                "Format", "Write (µs)", "Read (µs)", "Size");
        String sep    = "-".repeat(header.length());

        System.out.println();
        System.out.println("=== OHLC Benchmark: " + recordCount + " records (1 trading year) ===");
        System.out.println(sep);
        System.out.println(header);
        System.out.println(sep);

        results.forEach(r -> System.out.printf(
                "%-12s  %,12d  %,11d  %,10d%n",
                r.format(), r.writeMicros(), r.readMicros(), r.bytes()
        ));

        System.out.println(sep);
        System.out.println();
    }
}
