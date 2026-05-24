package io.github.dfa1.datastore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.stream.LongStream;

class BenchmarkTest {

    private static final int TRADING_DAYS_PER_YEAR = 252;
    private static final List<Duration> SCALES = List.of(1, 10, 20, 30, 40, 50, 100).stream()
            .map(y -> Duration.ofDays((long) y * TRADING_DAYS_PER_YEAR))
            .toList();

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

    private record Stats(Duration avg, Duration min, Duration max) {}
    private record Result(StoreType type, long bytes, Stats write, Stats read, Stats colRead) {}

    @Test
    void storageComparison(@TempDir Path tmp) throws Exception {
        System.out.println();

        for (Duration scale : SCALES) {
            int records = (int) scale.toDays();
            int years   = records / TRADING_DAYS_PER_YEAR;
            List<OhlcRecord> data = new OhlcGenerator(new Symbol("ACME"), LocalDate.of(2023, 1, 2), 100.0, 42L)
                    .stream(records).toList();

            var results = STORES.stream()
                    .map(store -> measure(store, data, tmp))
                    .toList();

            printTable(years, records, results);
        }
    }

    private Result measure(OhlcStore store, List<OhlcRecord> records, Path dir) {
        StoreType type = store.storeType();
        Path      file = dir.resolve("ohlc." + type.label().toLowerCase().replace("+", "-"));
        try {
            for (int i = 0; i < WARMUP_RUNS; i++) {
                store.write(records.stream(), file);
                store.read(file);
                store.readColumn(file, NumericColumn.CLOSE);
            }

            long[] writeSamples   = new long[MEASUREMENT_RUNS];
            long[] readSamples    = new long[MEASUREMENT_RUNS];
            long[] colReadSamples = new long[MEASUREMENT_RUNS];
            for (int i = 0; i < MEASUREMENT_RUNS; i++) {
                long t0 = System.nanoTime();
                store.write(records.stream(), file);
                writeSamples[i] = System.nanoTime() - t0;

                long t1 = System.nanoTime();
                store.read(file);
                readSamples[i] = System.nanoTime() - t1;

                long t2 = System.nanoTime();
                store.readColumn(file, NumericColumn.CLOSE);
                colReadSamples[i] = System.nanoTime() - t2;
            }

            return new Result(type, Files.size(file),
                    stats(writeSamples), stats(readSamples), stats(colReadSamples));
        } catch (Exception e) {
            throw new RuntimeException("store " + type.label() + " failed", e);
        }
    }

    private static Stats stats(long[] samples) {
        LongSummaryStatistics s = LongStream.of(samples).summaryStatistics();
        return new Stats(
                Duration.ofNanos((long) s.getAverage()),
                Duration.ofNanos(s.getMin()),
                Duration.ofNanos(s.getMax()));
    }

    private static double ms(Duration d) {
        return d.toNanos() / 1_000_000.0;
    }

    private void printTable(int years, int scale, List<Result> results) {
        long baseline = results.getFirst().bytes();

        String header = "%-14s  %12s  %8s  %30s  %30s  %8s".formatted(
                "Format", "Size (bytes)", "vs CSV", "Write ms (avg/min/max)", "Read ms (avg/min/max)", "read spd");
        String sep   = "-".repeat(header.length());
        String label = years + "y";

        long csvReadNs = results.getFirst().read().avg().toNanos();

        System.out.println("=== " + scale + " records (" + label + ") ===");
        System.out.println(sep);
        System.out.println(header);
        System.out.println(sep);

        results.forEach(r -> {
            double readSpeedup = csvReadNs == 0 ? Double.NaN
                    : (double) csvReadNs / Math.max(1, r.read().avg().toNanos());
            System.out.printf(
                "%-14s  %,12d  %7.1fx  %8.2f / %7.2f / %7.2f    %8.2f / %7.2f / %7.2f  %6.1fx%n",
                r.type().label(), r.bytes(), (double) r.bytes() / baseline,
                ms(r.write().avg()), ms(r.write().min()), ms(r.write().max()),
                ms(r.read().avg()),  ms(r.read().min()),  ms(r.read().max()),
                readSpeedup);
        });

        System.out.println(sep);

        String colHeader = "%-14s  %36s  %7s  %36s  %7s".formatted(
                "Format", "Full read ms (avg/min/max)", "speedup", "Col read ms (avg/min/max)", "speedup");
        String colSep = "-".repeat(colHeader.length());
        System.out.println();
        System.out.println("  readColumn(CLOSE):");
        System.out.println(colSep);
        System.out.println(colHeader);
        System.out.println(colSep);
        results.forEach(r -> {
            long readNs    = r.read().avg().toNanos();
            long colReadNs = r.colRead().avg().toNanos();
            double readSpeedup = csvReadNs == 0 ? Double.NaN : (double) csvReadNs / Math.max(1, readNs);
            double colSpeedup  = readNs == 0    ? Double.NaN : (double) readNs    / Math.max(1, colReadNs);
            System.out.printf("%-14s  %8.2f / %7.2f / %7.2f   %5.1fx  %8.2f / %7.2f / %7.2f   %5.1fx%n",
                    r.type().label(),
                    ms(r.read().avg()),    ms(r.read().min()),    ms(r.read().max()),
                    readSpeedup,
                    ms(r.colRead().avg()), ms(r.colRead().min()), ms(r.colRead().max()),
                    colSpeedup);
        });
        System.out.println(colSep);
        System.out.println();
    }
}
