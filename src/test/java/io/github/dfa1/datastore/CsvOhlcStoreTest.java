package io.github.dfa1.datastore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvOhlcStoreTest extends AbstractOhlcStoreTest {

    @Override
    OhlcStore createSut() { return new CsvOhlcStore(); }

    @Override
    StoreType expectedStoreType() { return StoreType.CSV; }

    @Test
    void fileHasHeader(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("ohlc.csv");
        createSut().write(new OhlcGenerator(new Symbol("X"), LocalDate.of(2024, 1, 2), 50.0, 1L).stream(1), file);
        assertEquals("date,symbol,open,high,low,close,volume", Files.readAllLines(file).getFirst());
    }

    @Test
    void lineCountMatchesRecords(@TempDir Path tmp) throws Exception {
        int count = 500;
        Path file = tmp.resolve("ohlc.csv");
        createSut().write(new OhlcGenerator(new Symbol("Y"), LocalDate.of(2023, 6, 1), 200.0, 7L).stream(count), file);
        assertEquals(count + 1, Files.lines(file).count()); // +1 for header
    }

}
