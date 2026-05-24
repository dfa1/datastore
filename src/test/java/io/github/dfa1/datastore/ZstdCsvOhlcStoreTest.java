package io.github.dfa1.datastore;

class ZstdCsvOhlcStoreTest extends AbstractOhlcStoreTest {

    @Override
    OhlcStore createSut() { return new ZstdCsvOhlcStore(); }

    @Override
    StoreType expectedStoreType() { return StoreType.CSV_ZSTD; }
}
