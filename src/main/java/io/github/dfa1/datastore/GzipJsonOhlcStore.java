package io.github.dfa1.datastore;

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
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipJsonOhlcStore implements OhlcStore {

    @Override
    public StoreType storeType() { return StoreType.JSON_GZIP; }

    @Override
    public void write(Stream<OhlcRecord> records, Path path) throws IOException {
        try (var gzip = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(path)));
             var out  = new BufferedWriter(new OutputStreamWriter(gzip, StandardCharsets.UTF_8))) {
            var it = records.iterator();
            while (it.hasNext()) {
                out.write(JsonOhlcStore.MAPPER.writeValueAsString(it.next()));
                out.newLine();
            }
        }
    }

    @Override
    public List<OhlcRecord> read(Path path) throws IOException {
        try (var gzip = new GZIPInputStream(new BufferedInputStream(Files.newInputStream(path)));
             var in   = new BufferedReader(new InputStreamReader(gzip, StandardCharsets.UTF_8))) {
            return JsonOhlcStore.MAPPER.readerFor(OhlcRecord.class)
                    .<OhlcRecord>readValues(in)
                    .readAll();
        }
    }
}
