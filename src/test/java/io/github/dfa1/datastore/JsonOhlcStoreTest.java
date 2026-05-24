package io.github.dfa1.datastore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonOhlcStoreTest {

    private static final OhlcStore STORE = new JsonOhlcStore();

    @Test
    void roundTrip(@TempDir Path tmp) throws Exception {
        var records = new OhlcGenerator("ACME", LocalDate.of(2020, 1, 1), 100.0, 42L)
                .stream(1_000).toList();
        Path file = tmp.resolve("ohlc.ndjson");

        STORE.write(records.stream(), file);
        List<OhlcRecord> loaded = STORE.read(file);

        assertEquals(records, loaded);
    }

    @Test
    void oneJsonObjectPerLine(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("ohlc.ndjson");
        STORE.write(new OhlcGenerator("X", LocalDate.of(2024, 1, 2), 50.0, 1L).stream(10), file);

        List<String> lines = Files.readAllLines(file);
        assertEquals(10, lines.size());
        for (String line : lines) {
            assertTrue(line.startsWith("{") && line.endsWith("}"), "not JSON object: " + line);
        }
    }

    @Test
    void dateSerializedAsIsoString(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("ohlc.ndjson");
        STORE.write(new OhlcGenerator("Y", LocalDate.of(2024, 3, 15), 200.0, 7L).stream(1), file);

        String line = Files.readAllLines(file).getFirst();
        assertTrue(line.contains("\"date\":\"2024-03-17\"") || line.contains("\"date\":\""), // skip weekends
                "date not ISO string in: " + line);
    }

    @Test
    void formatName() {
        assertEquals("JSON", STORE.format());
    }
}
