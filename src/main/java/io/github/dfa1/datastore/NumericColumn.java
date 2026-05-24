package io.github.dfa1.datastore;

import java.util.function.ToDoubleFunction;

public enum NumericColumn {
    OPEN(OhlcRecord::open),
    HIGH(OhlcRecord::high),
    LOW(OhlcRecord::low),
    CLOSE(OhlcRecord::close),
    VOLUME(r -> (double) r.volume());

    private final ToDoubleFunction<OhlcRecord> extractor;

    NumericColumn(ToDoubleFunction<OhlcRecord> extractor) {
        this.extractor = extractor;
    }

    public double extract(OhlcRecord r) {
        return extractor.applyAsDouble(r);
    }

    String fieldName() {
        return name().toLowerCase();
    }
}
