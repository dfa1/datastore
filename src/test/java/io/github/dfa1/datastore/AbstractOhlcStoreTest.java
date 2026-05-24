package io.github.dfa1.datastore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

abstract class AbstractOhlcStoreTest {

    abstract OhlcStore createSut();

    @Test
    void roundTrip(@TempDir Path tmp) throws Exception {
        var records = new OhlcGenerator("ACME", LocalDate.of(2020, 1, 1), 100.0, 42L)
                .stream(1_000).toList();
        Path file = tmp.resolve("ohlc.dat");
        OhlcStore sut = createSut();
        sut.write(records.stream(), file);
        assertEquals(records, sut.read(file));
    }
}
