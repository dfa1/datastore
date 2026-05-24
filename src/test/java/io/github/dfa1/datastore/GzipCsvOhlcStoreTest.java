package io.github.dfa1.datastore;

class GzipCsvOhlcStoreTest extends AbstractOhlcStoreTest {

    @Override
    OhlcStore createSut() { return new GzipCsvOhlcStore(); }

    @Override
    StoreType expectedStoreType() { return StoreType.CSV_GZIP; }
}
