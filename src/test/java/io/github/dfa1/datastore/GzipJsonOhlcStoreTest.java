package io.github.dfa1.datastore;

class GzipJsonOhlcStoreTest extends AbstractOhlcStoreTest {

    @Override
    OhlcStore createSut() { return new GzipJsonOhlcStore(); }

    @Override
    StoreType expectedStoreType() { return StoreType.JSON_GZIP; }
}
