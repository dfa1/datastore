package io.github.dfa1.datastore;

import org.apache.parquet.hadoop.metadata.CompressionCodecName;

public class ZstdParquetOhlcStore extends ParquetOhlcStore {

    public ZstdParquetOhlcStore() {
        super(CompressionCodecName.ZSTD, StoreType.PARQUET_ZSTD);
    }
}
