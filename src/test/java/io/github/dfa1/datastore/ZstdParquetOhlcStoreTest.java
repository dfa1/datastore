package io.github.dfa1.datastore;

class ZstdParquetOhlcStoreTest extends AbstractOhlcStoreTest {

    @Override
    OhlcStore createSut() { return new ZstdParquetOhlcStore(); }

    @Override
    StoreType expectedStoreType() { return StoreType.PARQUET_ZSTD; }
}
