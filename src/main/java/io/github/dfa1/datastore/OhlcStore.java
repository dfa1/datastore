package io.github.dfa1.datastore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public interface OhlcStore {
    void write(Stream<OhlcRecord> records, Path path) throws IOException;
    List<OhlcRecord> read(Path path) throws IOException;
    StoreType storeType();

    default double[] readColumn(Path path, NumericColumn column) throws IOException {
        List<OhlcRecord> records = read(path);
        double[] result = new double[records.size()];
        for (int i = 0; i < records.size(); i++) {
            result[i] = column.extract(records.get(i));
        }
        return result;
    }
}
