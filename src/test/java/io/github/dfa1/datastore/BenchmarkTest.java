package io.github.dfa1.datastore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

class BenchmarkTest {

    private static final List<Integer> SCALES = List.of(252, 2_520, 10_000, 100_000);

    private static final List<OhlcStore> STORES = List.of(
            new CsvOhlcStore(),
            new GzipCsvOhlcStore(),
            new JsonOhlcStore(),
            new GzipJsonOhlcStore(),
            new ParquetOhlcStore(),
            new GzipParquetOhlcStore(),
            new ZstdCsvOhlcStore(),
            new ZstdJsonOhlcStore(),
            new ZstdParquetOhlcStore(),
            new VortexOhlcStore()
    );

    private record Result(String format, long bytes, long writeMs, long readMs) {}

    @Test
    void storageComparison(@TempDir Path tmp) throws Exception {
        System.out.println();

        for (int scale : SCALES) {
            List<OhlcRecord> records = new OhlcGenerator("ACME", LocalDate.of(2023, 1, 2), 100.0, 42L)
                    .stream(scale).toList();

            var results = STORES.stream()
                    .map(store -> measure(store, records, tmp))
                    .toList();

            printTable(scale, results);
        }
    }

    private Result measure(OhlcStore store, List<OhlcRecord> records, Path dir) {
        String ext  = store.storeType().label().toLowerCase().replace("+", "-");
        Path   file = dir.resolve("ohlc." + ext);
        try {
            long t0 = System.currentTimeMillis();
            store.write(records.stream(), file);
            long writeMs = System.currentTimeMillis() - t0;

            long t1 = System.currentTimeMillis();
            store.read(file);
            long readMs = System.currentTimeMillis() - t1;

            return new Result(store.storeType().label(), Files.size(file), writeMs, readMs);
        } catch (Exception e) {
            throw new RuntimeException("store " + store.storeType().label() + " failed", e);
        }
    }

    private void printTable(int scale, List<Result> results) {
        long baseline = results.getFirst().bytes(); // CSV is baseline

        String header = "%-14s  %12s  %8s  %9s  %8s".formatted(
                "Format", "Size (bytes)", "vs CSV", "Write ms", "Read ms");
        String sep   = "-".repeat(header.length());
        String label = scale == 2_520 ? "10y" : scale >= 1_000 ? (scale / 1_000) + "k" : String.valueOf(scale);

        System.out.println("=== " + scale + " records (" + label + ") ===");
        System.out.println(sep);
        System.out.println(header);
        System.out.println(sep);

        results.forEach(r -> System.out.printf(
                "%-14s  %,12d  %7.1fx  %9d  %8d%n",
                r.format(), r.bytes(), (double) r.bytes() / baseline, r.writeMs(), r.readMs()
        ));

        System.out.println(sep);
        System.out.println();
    }
}
