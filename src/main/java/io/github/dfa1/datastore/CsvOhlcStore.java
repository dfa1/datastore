package io.github.dfa1.datastore;

import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.NamedCsvRecord;
import de.siegmar.fastcsv.writer.CsvWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class CsvOhlcStore implements OhlcStore {

    @Override
    public String format() {
        return "CSV";
    }

    @Override
    public void write(Stream<OhlcRecord> records, Path path) throws IOException {
        try (var writer = CsvWriter.builder().build(path)) {
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
        try (var reader = CsvReader.builder().ofNamedCsvRecord(path)) {
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
