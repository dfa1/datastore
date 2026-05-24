package io.github.dfa1.datastore;

class GzipParquetOhlcStoreTest extends AbstractOhlcStoreTest {

    @Override
    OhlcStore createSut() { return new GzipParquetOhlcStore(); }

    @Override
    StoreType expectedStoreType() { return StoreType.PARQUET_GZIP; }
}
