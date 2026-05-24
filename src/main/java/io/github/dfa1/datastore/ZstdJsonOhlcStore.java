package io.github.dfa1.datastore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class ZstdJsonOhlcStore implements OhlcStore {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public StoreType storeType() { return StoreType.JSON_ZSTD; }

    @Override
    public void write(Stream<OhlcRecord> records, Path path) throws IOException {
        try (var zstd = new ZstdOutputStream(new BufferedOutputStream(Files.newOutputStream(path)));
             var out  = new BufferedWriter(new OutputStreamWriter(zstd, StandardCharsets.UTF_8))) {
            var it = records.iterator();
            while (it.hasNext()) {
                out.write(MAPPER.writeValueAsString(it.next()));
                out.newLine();
            }
        }
    }

    @Override
    public List<OhlcRecord> read(Path path) throws IOException {
        try (var zstd = new ZstdInputStream(new BufferedInputStream(Files.newInputStream(path)));
             var in   = new BufferedReader(new InputStreamReader(zstd, StandardCharsets.UTF_8))) {
            return MAPPER.readerFor(OhlcRecord.class)
                    .<OhlcRecord>readValues(in)
                    .readAll();
        }
    }
}
