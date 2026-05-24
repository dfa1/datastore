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

    private record Result(String format, long bytes) {}

    @Test
    void storageComparison(@TempDir Path tmp) throws Exception {
        System.out.println();

        for (int scale : SCALES) {
            var records = new OhlcGenerator("ACME", LocalDate.of(2023, 1, 2), 100.0, 42L)
                    .generate(scale);

            var results = STORES.stream()
                    .map(store -> write(store, records, tmp))
                    .toList();

            printTable(scale, results);
        }
    }

    private Result write(OhlcStore store, List<OhlcRecord> records, Path dir) {
        String ext  = store.format().toLowerCase().replace("+", "-");
        Path   file = dir.resolve("ohlc." + ext);
        try {
            store.write(records, file);
            return new Result(store.format(), Files.size(file));
        } catch (Exception e) {
            throw new RuntimeException("store " + store.format() + " failed", e);
        }
    }

    private void printTable(int scale, List<Result> results) {
        long baseline = results.getFirst().bytes(); // CSV is baseline

        String header = "%-12s  %12s  %8s".formatted("Format", "Size (bytes)", "vs CSV");
        String sep    = "-".repeat(header.length());
        String label  = scale == 2_520 ? "10y" : scale >= 1_000 ? (scale / 1_000) + "k" : String.valueOf(scale);

        System.out.println("=== " + scale + " records (" + label + ") ===");
        System.out.println(sep);
        System.out.println(header);
        System.out.println(sep);

        results.forEach(r -> System.out.printf(
                "%-12s  %,12d  %7.1fx%n",
                r.format(), r.bytes(), (double) r.bytes() / baseline
        ));

        System.out.println(sep);
        System.out.println();
    }
}
