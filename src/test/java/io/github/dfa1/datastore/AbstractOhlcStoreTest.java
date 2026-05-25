package io.github.dfa1.datastore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

abstract class AbstractOhlcStoreTest {

    abstract OhlcStore createSut();

    abstract StoreType expectedStoreType();

    @Test
    void storeType() {
        // Given
        OhlcStore sut = createSut();

        // When
        StoreType result = sut.storeType();

        // Then
        assertEquals(expectedStoreType(), result);
    }

    @Test
    void roundTrip(@TempDir Path tmp) throws Exception {
        // Given
        OhlcStore sut = createSut();
        List<OhlcRecord> records = new OhlcGenerator(new Symbol("ACME"), LocalDate.of(2020, 1, 1), 100.0, 42L)
                .stream(1_000).toList();
        Path file = tmp.resolve("ohlc.dat");
        sut.write(records.stream(), file);

        // When
        List<OhlcRecord> result = sut.read(file);

        // Then
        assertEquals(records, result);
    }
}
