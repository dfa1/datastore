package io.github.dfa1.datastore;

import org.apache.parquet.hadoop.metadata.CompressionCodecName;

public class ZstdTypedParquetOhlcStore extends TypedParquetOhlcStore {

    public ZstdTypedParquetOhlcStore() {
        super(CompressionCodecName.ZSTD, "Parquet-T+ZSTD");
    }
}
