package io.github.dfa1.datastore;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

class ZstdCompressionDiagTest {

    @Test
    void compareZstdApproaches(@TempDir Path tmp) throws Exception {
        // Given
        List<OhlcRecord> records = new OhlcGenerator(new Symbol("ACME"), LocalDate.of(2020, 1, 1), 100.0, 42L)
                .stream(10_000).toList();

        Path csv  = tmp.resolve("data.csv");
        Path zst1 = tmp.resolve("data-stream-nobuf.csv.zst");
        Path zst2 = tmp.resolve("data-stream-buf.csv.zst");
        Path zst3 = tmp.resolve("data-singleshot.csv.zst");

        new CsvOhlcStore().write(records.stream(), csv);
        new ZstdCsvOhlcStore().write(records.stream(), zst1);

        try (var zstd = new ZstdOutputStream(new BufferedOutputStream(Files.newOutputStream(zst2)), 9);
             var out  = new OutputStreamWriter(zstd, StandardCharsets.UTF_8);
             var sw   = CsvOhlcStore.MAPPER.writer(CsvOhlcStore.SCHEMA).writeValues(out)) {
            var it = records.stream().iterator();
            while (it.hasNext()) sw.write(it.next());
        }

        byte[] rawCsv = Files.readAllBytes(csv);

        // When
        byte[] compressed = Zstd.compress(rawCsv, 3);
        Files.write(zst3, compressed);

        // Then (diagnostic output — no assertions, sizes printed for manual comparison)
        long csvSize = Files.size(csv);
        System.out.printf("%nCSV:                   %,d bytes%n", csvSize);
        System.out.printf("ZSTD stream (no buf):  %,d bytes (%.2fx)%n", Files.size(zst1), (double) csvSize / Files.size(zst1));
        System.out.printf("ZSTD stream (buf):     %,d bytes (%.2fx)%n", Files.size(zst2), (double) csvSize / Files.size(zst2));
        System.out.printf("ZSTD single-shot:      %,d bytes (%.2fx)%n", Files.size(zst3), (double) csvSize / Files.size(zst3));
    }
}
