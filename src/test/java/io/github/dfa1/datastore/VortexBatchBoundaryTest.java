package io.github.dfa1.datastore;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VortexBatchBoundaryTest {

    private static final int BATCH_SIZE = 65_536;

    @ParameterizedTest
    @ValueSource(ints = {BATCH_SIZE - 1, BATCH_SIZE, BATCH_SIZE + 1})
    void roundTripAcrossBatchBoundary(int count, @TempDir Path tmp) throws Exception {
        // Given
        VortexOhlcStore store = new VortexOhlcStore();
        List<OhlcRecord> records = new OhlcGenerator(new Symbol("ACME"), LocalDate.of(2020, 1, 1), 100.0, 42L)
                .stream(count).toList();
        Path file = tmp.resolve("ohlc-" + count + ".vortex");
        store.write(records.stream(), file);

        // When
        List<OhlcRecord> result = store.read(file);

        // Then
        assertEquals(records.size(), result.size());
        assertEquals(records, result);
    }
}
