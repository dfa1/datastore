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
 * Runs 3 warm-up + 5 measured iterations per (store × scale); reports avg write/read µs and file size.
 */
class BenchmarkTest {

    private static final List<Integer> SCALES = List.of(252, 10_000, 100_000);
    private static final int WARMUP     = 3;
    private static final int ITERATIONS = 5;

    private static final List<OhlcStore> STORES = List.of(
            new CsvOhlcStore(),
            new GzipCsvOhlcStore(),
            new JsonOhlcStore(),
            new GzipJsonOhlcStore(),
            new ParquetOhlcStore()
    );

    private record Result(String format, int records, long writeMicros, long readMicros, long bytes) {}

    @Test
    void compareFormats(@TempDir Path tmp) throws Exception {
        System.out.println();

        for (int scale : SCALES) {
            var records = new OhlcGenerator("ACME", LocalDate.of(2023, 1, 2), 100.0, 42L)
                    .generate(scale);

            var results = STORES.stream()
                    .map(store -> measure(store, records, tmp))
                    .toList();

            printTable(scale, results);

            results.forEach(r -> assertEquals(scale, r.records()));
        }
    }

    private Result measure(OhlcStore store, List<OhlcRecord> records, Path dir) {
        String ext  = store.format().toLowerCase().replace("+", "-");
        Path   file = dir.resolve("ohlc." + ext);

        long totalWrite = 0, totalRead = 0;
        int  loaded     = 0;

        for (int i = 0; i < WARMUP + ITERATIONS; i++) {
            try {
                Files.deleteIfExists(file);

                long t0 = System.nanoTime();
                store.write(records, file);
                long t1 = System.nanoTime();
                var read = store.read(file);
                long t2 = System.nanoTime();

                if (i >= WARMUP) {
                    totalWrite += (t1 - t0);
                    totalRead  += (t2 - t1);
                    loaded      = read.size();
                }
            } catch (Exception e) {
                throw new RuntimeException("store " + store.format() + " failed at scale " + records.size(), e);
            }
        }

        long fileBytes = 0;
        try { fileBytes = Files.size(file); } catch (Exception ignored) {}

        return new Result(
                store.format(),
                loaded,
                totalWrite / ITERATIONS / 1_000,
                totalRead  / ITERATIONS / 1_000,
                fileBytes
        );
    }

    private void printTable(int scale, List<Result> results) {
        String label  = scale >= 1_000 ? (scale / 1_000) + "k" : String.valueOf(scale);
        String header = "%-12s  %12s  %11s  %12s".formatted("Format", "Write (µs)", "Read (µs)", "Size (bytes)");
        String sep    = "-".repeat(header.length());

        System.out.println("=== " + scale + " records (" + label + ") ===");
        System.out.println(sep);
        System.out.println(header);
        System.out.println(sep);

        results.forEach(r -> System.out.printf(
                "%-12s  %,12d  %,11d  %,12d%n",
                r.format(), r.writeMicros(), r.readMicros(), r.bytes()
        ));

        System.out.println(sep);
        System.out.println();
    }
}
