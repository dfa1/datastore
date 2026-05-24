package io.github.dfa1.datastore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParquetOhlcStoreTest extends AbstractOhlcStoreTest {

    @Override
    OhlcStore createSut() { return new ParquetOhlcStore(); }

    @Override
    StoreType expectedStoreType() { return StoreType.PARQUET; }
}
