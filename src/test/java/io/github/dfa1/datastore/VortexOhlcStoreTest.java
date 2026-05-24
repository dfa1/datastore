package io.github.dfa1.datastore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VortexOhlcStoreTest extends AbstractOhlcStoreTest {

    @Override
    OhlcStore createSut() { return new VortexOhlcStore(); }

    @Test
    void formatName() {
        assertEquals("Vortex", createSut().format());
    }
}
