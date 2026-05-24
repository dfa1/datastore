package io.github.dfa1.datastore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonOhlcStoreTest extends AbstractOhlcStoreTest {

    @Override
    OhlcStore createSut() { return new JsonOhlcStore(); }

    @Override
    StoreType expectedStoreType() { return StoreType.JSON; }

    @Test
    void oneJsonObjectPerLine(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("ohlc.ndjson");
        createSut().write(new OhlcGenerator(new Symbol("X"), LocalDate.of(2024, 1, 2), 50.0, 1L).stream(10), file);
        List<String> lines = Files.readAllLines(file);
        assertEquals(10, lines.size());
        for (String line : lines) {
            assertTrue(line.startsWith("{") && line.endsWith("}"), "not JSON object: " + line);
        }
    }

    @Test
    void dateSerializedAsIsoString(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("ohlc.ndjson");
        createSut().write(new OhlcGenerator(new Symbol("Y"), LocalDate.of(2024, 3, 15), 200.0, 7L).stream(1), file);
        String line = Files.readAllLines(file).getFirst();
        assertTrue(line.contains("\"date\":\""), "date not ISO string in: " + line);
    }

}
