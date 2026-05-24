package io.github.dfa1.datastore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** One JSON object per line (NDJSON). */
public class JsonOhlcStore implements OhlcStore {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public String format() {
        return "JSON";
    }

    @Override
    public void write(List<OhlcRecord> records, Path path) throws IOException {
        try (var out = Files.newBufferedWriter(path)) {
            for (var r : records) {
                out.write(MAPPER.writeValueAsString(r));
                out.newLine();
            }
        }
    }

    @Override
    public List<OhlcRecord> read(Path path) throws IOException {
        try (var in = Files.newBufferedReader(path)) {
            return MAPPER.readerFor(OhlcRecord.class)
                    .<OhlcRecord>readValues(in)
                    .readAll();
        }
    }
}
