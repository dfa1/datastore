package io.github.dfa1.datastore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParquetOhlcStoreTest {

    private static final OhlcStore STORE = new ParquetOhlcStore();

    @Test
    void roundTrip(@TempDir Path tmp) throws Exception {
        var records = new OhlcGenerator("ACME", LocalDate.of(2020, 1, 1), 100.0, 42L)
                .stream(1_000).toList();
        Path file = tmp.resolve("ohlc.parquet");

        STORE.write(records.stream(), file);
        List<OhlcRecord> loaded = STORE.read(file);

        assertEquals(records, loaded);
    }

    @Test
    void formatName() {
        assertEquals("Parquet", STORE.format());
    }
}
