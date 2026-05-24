package io.github.dfa1.datastore;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class CsvOhlcStore implements OhlcStore {

    static final CsvMapper MAPPER;
    static final CsvSchema SCHEMA;

    static {
        MAPPER = new CsvMapper();
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        SCHEMA = CsvSchema.builder()
                .addColumn("date")
                .addColumn("symbol")
                .addNumberColumn("open")
                .addNumberColumn("high")
                .addNumberColumn("low")
                .addNumberColumn("close")
                .addNumberColumn("volume")
                .setUseHeader(true)
                .build();
    }

    @Override
    public StoreType storeType() { return StoreType.CSV; }

    @Override
    public void write(Stream<OhlcRecord> records, Path path) throws IOException {
        try (var out = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
             var sw  = MAPPER.writer(SCHEMA).writeValues(out)) {
            var it = records.iterator();
            while (it.hasNext()) sw.write(it.next());
        }
    }

    @Override
    public List<OhlcRecord> read(Path path) throws IOException {
        try (var in = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             var it = MAPPER.readerFor(OhlcRecord.class)
                            .with(SCHEMA)
                            .<OhlcRecord>readValues(in)) {
            return it.readAll();
        }
    }
}
