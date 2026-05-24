package io.github.dfa1.datastore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface OhlcStore {
    void write(List<OhlcRecord> records, Path path) throws IOException;
    List<OhlcRecord> read(Path path) throws IOException;
    String format();
}
