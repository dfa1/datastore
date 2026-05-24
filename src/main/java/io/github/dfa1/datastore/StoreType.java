package io.github.dfa1.datastore;

public enum StoreType {
    CSV("CSV"),
    CSV_GZIP("CSV+GZIP"),
    CSV_ZSTD("CSV+ZSTD"),
    JSON("JSON"),
    JSON_GZIP("JSON+GZIP"),
    JSON_ZSTD("JSON+ZSTD"),
    PARQUET("Parquet"),
    PARQUET_GZIP("Parquet+GZIP"),
    PARQUET_ZSTD("Parquet+ZSTD"),
    VORTEX("Vortex");

    private final String label;

    StoreType(String label) { this.label = label; }

    public String label() { return label; }
}
