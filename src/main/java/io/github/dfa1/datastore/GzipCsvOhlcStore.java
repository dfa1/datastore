package io.github.dfa1.datastore;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipCsvOhlcStore implements OhlcStore {

    @Override
    public StoreType storeType() { return StoreType.CSV_GZIP; }

    @Override
    public void write(Stream<OhlcRecord> records, Path path) throws IOException {
        try (var gzip = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(path)));
             var out  = new OutputStreamWriter(gzip, StandardCharsets.UTF_8);
             var sw   = CsvOhlcStore.MAPPER.writer(CsvOhlcStore.SCHEMA).writeValues(out)) {
            var it = records.iterator();
            while (it.hasNext()) sw.write(it.next());
        }
    }

    @Override
    public List<OhlcRecord> read(Path path) throws IOException {
        try (var gzip = new GZIPInputStream(new BufferedInputStream(Files.newInputStream(path)));
             var in   = new InputStreamReader(gzip, StandardCharsets.UTF_8);
             var it   = CsvOhlcStore.MAPPER.readerFor(OhlcRecord.class)
                                           .with(CsvOhlcStore.SCHEMA)
                                           .<OhlcRecord>readValues(in)) {
            return it.readAll();
        }
    }
}
