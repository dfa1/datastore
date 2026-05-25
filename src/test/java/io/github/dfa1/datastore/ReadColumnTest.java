package io.github.dfa1.datastore;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReadColumnTest {

    @TempDir
    Path tmp;

    static Stream<OhlcStore> stores() {
        return Stream.of(
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
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stores")
    void readColumnMatchesFullRead(OhlcStore store) throws Exception {
        // Given
        List<OhlcRecord> records = new OhlcGenerator(new Symbol("ACME"), LocalDate.of(2020, 1, 1), 100.0, 42L)
                .stream(500).toList();
        Path file = tmp.resolve("ohlc." + store.storeType().name().toLowerCase());
        store.write(records.stream(), file);

        for (NumericColumn col : NumericColumn.values()) {
            double[] expected = records.stream().mapToDouble(col::extract).toArray();

            // When
            double[] actual = store.readColumn(file, col);

            // Then
            assertEquals(expected.length, actual.length, store.storeType() + " / " + col + ": length");
            assertArrayEquals(expected, actual, 1e-9, store.storeType() + " / " + col + ": values");
        }
    }
}
