package io.github.dfa1.datastore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public interface OhlcStore {
    void write(Stream<OhlcRecord> records, Path path) throws IOException;
    List<OhlcRecord> read(Path path) throws IOException;
    String format();
}
