package io.github.dfa1.datastore;

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.NamedCsvRecord;
import de.siegmar.fastcsv.writer.CsvWriter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class ZstdCsvOhlcStore implements OhlcStore {

    @Override
    public String format() {
        return "CSV+ZSTD";
    }

    @Override
    public void write(Stream<OhlcRecord> records, Path path) throws IOException {
        try (var zstd   = new ZstdOutputStream(new BufferedOutputStream(Files.newOutputStream(path)));
             var writer = CsvWriter.builder().build(new OutputStreamWriter(zstd, StandardCharsets.UTF_8))) {
            writer.writeRecord("date", "symbol", "open", "high", "low", "close", "volume");
            var it = records.iterator();
            while (it.hasNext()) {
                var r = it.next();
                writer.writeRecord(
                        r.date().toString(),
                        r.symbol(),
                        Double.toString(r.open()),
                        Double.toString(r.high()),
                        Double.toString(r.low()),
                        Double.toString(r.close()),
                        Long.toString(r.volume())
                );
            }
        }
    }

    @Override
    public List<OhlcRecord> read(Path path) throws IOException {
        var records = new ArrayList<OhlcRecord>();
        try (var zstd   = new ZstdInputStream(new BufferedInputStream(Files.newInputStream(path)));
             var reader = CsvReader.builder().ofNamedCsvRecord(new InputStreamReader(zstd, StandardCharsets.UTF_8))) {
            for (NamedCsvRecord row : reader) {
                records.add(new OhlcRecord(
                        LocalDate.parse(row.getField("date")),
                        row.getField("symbol"),
                        Double.parseDouble(row.getField("open")),
                        Double.parseDouble(row.getField("high")),
                        Double.parseDouble(row.getField("low")),
                        Double.parseDouble(row.getField("close")),
                        Long.parseLong(row.getField("volume"))
                ));
            }
        }
        return records;
    }
}
