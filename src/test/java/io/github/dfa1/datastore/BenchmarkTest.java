package io.github.dfa1.datastore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.stream.LongStream;

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

    private static final int WARMUP_RUNS      = 2;
    private static final int MEASUREMENT_RUNS = 10;

    private record Stats(long avg, long min, long max) {}
    private record Result(String format, long bytes, Stats write, Stats read) {}

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
            for (int i = 0; i < WARMUP_RUNS; i++) {
                store.write(records.stream(), file);
                store.read(file);
            }

            long[] writeSamples = new long[MEASUREMENT_RUNS];
            long[] readSamples  = new long[MEASUREMENT_RUNS];
            for (int i = 0; i < MEASUREMENT_RUNS; i++) {
                long t0 = System.nanoTime();
                store.write(records.stream(), file);
                writeSamples[i] = (System.nanoTime() - t0) / 1_000_000;

                long t1 = System.nanoTime();
                store.read(file);
                readSamples[i] = (System.nanoTime() - t1) / 1_000_000;
            }

            return new Result(store.storeType().label(), Files.size(file),
                    stats(writeSamples), stats(readSamples));
        } catch (Exception e) {
            throw new RuntimeException("store " + store.storeType().label() + " failed", e);
        }
    }

    private static Stats stats(long[] samples) {
        LongSummaryStatistics s = LongStream.of(samples).summaryStatistics();
        return new Stats((long) s.getAverage(), s.getMin(), s.getMax());
    }

    private void printTable(int scale, List<Result> results) {
        long baseline = results.getFirst().bytes(); // CSV is baseline

        String header = "%-14s  %12s  %8s  %22s  %22s".formatted(
                "Format", "Size (bytes)", "vs CSV", "Write ms (avg/min/max)", "Read ms (avg/min/max)");
        String sep   = "-".repeat(header.length());
        String label = scale == 2_520 ? "10y" : scale >= 1_000 ? (scale / 1_000) + "k" : String.valueOf(scale);

        System.out.println("=== " + scale + " records (" + label + ") ===");
        System.out.println(sep);
        System.out.println(header);
        System.out.println(sep);

        results.forEach(r -> System.out.printf(
                "%-14s  %,12d  %7.1fx  %6d / %4d / %4d    %6d / %4d / %4d%n",
                r.format(), r.bytes(), (double) r.bytes() / baseline,
                r.write().avg(), r.write().min(), r.write().max(),
                r.read().avg(),  r.read().min(),  r.read().max()
        ));

        System.out.println(sep);
        System.out.println();
    }
}
