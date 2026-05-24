package io.github.dfa1.datastore;

class ZstdJsonOhlcStoreTest extends AbstractOhlcStoreTest {

    @Override
    OhlcStore createSut() { return new ZstdJsonOhlcStore(); }

    @Override
    StoreType expectedStoreType() { return StoreType.JSON_ZSTD; }
}
