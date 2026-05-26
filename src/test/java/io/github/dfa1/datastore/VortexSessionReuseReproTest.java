package io.github.dfa1.datastore;

import dev.vortex.jni.NativeLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

// Minimal repro for https://github.com/vortex-data/vortex/issues/8075
// A shared static Session in VortexOhlcStore accumulates stale state across write+read cycles,
// causing [errno 22] SerializedArray buffer is too short for flatbuffer when a larger file is
// written to the same path after repeated smaller cycles.
class VortexSessionReuseReproTest {

    static {
        NativeLoader.loadJni();
    }

    private static final int SMALL = 252;
    private static final int LARGE = 2520;

    // Mirrors the benchmark: 12 write+read+readColumn cycles on the same path at SMALL size,
    // then a LARGE write+read on the same path triggers the session bug.
    @Test
    void sharedSessionFailsAfterRepeatedCyclesThenLargerWrite(@TempDir Path tmp) throws IOException {
        // Given
        VortexOhlcStore store = new VortexOhlcStore();
        List<OhlcRecord> small = generate(SMALL);
        List<OhlcRecord> large = generate(LARGE);
        Path file = tmp.resolve("data.vortex");

        for (int i = 0; i < 12; i++) {
            store.write(small.stream(), file);
            store.read(file);
            store.readColumn(file, NumericColumn.CLOSE);
        }
        store.write(large.stream(), file);

        // When / Then - [errno 22] SerializedArray buffer is too short for flatbuffer
        assertThrows(IOException.class, () -> store.read(file));
    }

    private static List<OhlcRecord> generate(int n) {
        return new OhlcGenerator(new Symbol("ACME"), LocalDate.of(2020, 1, 1), 100.0, 42L)
                .stream(n).toList();
    }
}
