package io.github.dfa1.datastore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class JsonOhlcStore implements OhlcStore {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public StoreType storeType() { return StoreType.JSON; }

    @Override
    public void write(Stream<OhlcRecord> records, Path path) throws IOException {
        try (var out = Files.newBufferedWriter(path)) {
            var it = records.iterator();
            while (it.hasNext()) {
                out.write(MAPPER.writeValueAsString(it.next()));
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
