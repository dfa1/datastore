package io.github.dfa1.datastore;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CsvOhlcStore implements OhlcStore {

    private static final String HEADER = "date,symbol,open,high,low,close,volume";

    @Override
    public String format() {
        return "CSV";
    }

    @Override
    public void write(List<OhlcRecord> records, Path path) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(path)) {
            w.write(HEADER);
            w.newLine();
            for (var r : records) {
                w.write(r.date().toString());
                w.write(',');
                w.write(r.symbol());
                w.write(',');
                w.write(Double.toString(r.open()));
                w.write(',');
                w.write(Double.toString(r.high()));
                w.write(',');
                w.write(Double.toString(r.low()));
                w.write(',');
                w.write(Double.toString(r.close()));
                w.write(',');
                w.write(Long.toString(r.volume()));
                w.newLine();
            }
        }
    }

    @Override
    public List<OhlcRecord> read(Path path) throws IOException {
        var records = new ArrayList<OhlcRecord>();
        try (BufferedReader r = Files.newBufferedReader(path)) {
            r.readLine(); // skip header
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                records.add(parseLine(line));
            }
        }
        return records;
    }

    private static OhlcRecord parseLine(String line) {
        // hand-rolled split — avoids regex overhead for hot path
        int[] commas = new int[6];
        int found = 0;
        for (int i = 0; i < line.length() && found < 6; i++) {
            if (line.charAt(i) == ',') commas[found++] = i;
        }
        LocalDate date   = LocalDate.parse(line.substring(0, commas[0]));
        String   symbol  = line.substring(commas[0] + 1, commas[1]);
        double   open    = Double.parseDouble(line.substring(commas[1] + 1, commas[2]));
        double   high    = Double.parseDouble(line.substring(commas[2] + 1, commas[3]));
        double   low     = Double.parseDouble(line.substring(commas[3] + 1, commas[4]));
        double   close   = Double.parseDouble(line.substring(commas[4] + 1, commas[5]));
        long     volume  = Long.parseLong(line.substring(commas[5] + 1));
        return new OhlcRecord(date, symbol, open, high, low, close, volume);
    }
}
