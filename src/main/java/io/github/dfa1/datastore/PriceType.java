package io.github.dfa1.datastore;

import java.util.function.ToDoubleFunction;

public enum PriceType {
    OPEN(OhlcRecord::open),
    HIGH(OhlcRecord::high),
    LOW(OhlcRecord::low),
    CLOSE(OhlcRecord::close);

    private final ToDoubleFunction<OhlcRecord> extractor;

    PriceType(ToDoubleFunction<OhlcRecord> extractor) {
        this.extractor = extractor;
    }

    public double extract(OhlcRecord r) {
        return extractor.applyAsDouble(r);
    }

    String fieldName() {
        return name().toLowerCase();
    }
}
