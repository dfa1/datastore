package io.github.dfa1.datastore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvOhlcStoreTest {

    private static final OhlcStore STORE = new CsvOhlcStore();

    @Test
    void roundTrip(@TempDir Path tmp) throws Exception {
        var records = new OhlcGenerator("ACME", LocalDate.of(2020, 1, 1), 100.0, 42L)
                .generate(1_000);
        Path file = tmp.resolve("ohlc.csv");

        STORE.write(records, file);
        List<OhlcRecord> loaded = STORE.read(file);

        assertEquals(records, loaded);
    }

    @Test
    void fileHasHeader(@TempDir Path tmp) throws Exception {
        var records = new OhlcGenerator("X", LocalDate.of(2024, 1, 2), 50.0, 1L)
                .generate(1);
        Path file = tmp.resolve("ohlc.csv");
        STORE.write(records, file);

        String firstLine = java.nio.file.Files.readAllLines(file).getFirst();
        assertEquals("date,symbol,open,high,low,close,volume", firstLine);
    }

    @Test
    void lineCountMatchesRecords(@TempDir Path tmp) throws Exception {
        int count = 500;
        var records = new OhlcGenerator("Y", LocalDate.of(2023, 6, 1), 200.0, 7L)
                .generate(count);
        Path file = tmp.resolve("ohlc.csv");
        STORE.write(records, file);

        long lines = java.nio.file.Files.lines(file).count();
        assertEquals(count + 1, lines); // +1 for header
    }

    @Test
    void formatName() {
        assertEquals("CSV", STORE.format());
    }
}
