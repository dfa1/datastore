package io.github.dfa1.datastore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParquetOhlcStoreTest extends AbstractOhlcStoreTest {

    @Override
    OhlcStore createSut() { return new ParquetOhlcStore(); }

    @Test
    void formatName() {
        assertEquals("Parquet", createSut().format());
    }
}
