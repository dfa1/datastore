package io.github.dfa1.datastore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrossFormatConsistencyTest {

    private static final List<OhlcStore> STORES = List.of(
            new CsvOhlcStore(),
            new GzipCsvOhlcStore(),
            new ZstdCsvOhlcStore(),
            new JsonOhlcStore(),
            new GzipJsonOhlcStore(),
            new ZstdJsonOhlcStore(),
            new ParquetOhlcStore(),
            new GzipParquetOhlcStore(),
            new ZstdParquetOhlcStore(),
            new VortexOhlcStore()
    );

    @Test
    void allFormatsRoundTripIdentically(@TempDir Path tmp) throws Exception {
        // Given
        List<OhlcRecord> source = new OhlcGenerator(new Symbol("ACME"), LocalDate.of(2020, 1, 1), 100.0, 42L)
                .stream(1_000).toList();

        for (OhlcStore store : STORES) {
            Path file = tmp.resolve("ohlc." + store.storeType().name().toLowerCase());
            store.write(source.stream(), file);

            // When
            List<OhlcRecord> loaded = store.read(file);

            // Then
            assertEquals(source.size(), loaded.size(), store.storeType() + ": record count mismatch");
            assertEquals(source, loaded, store.storeType() + ": data mismatch");
        }
    }
}
