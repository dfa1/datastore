package io.github.dfa1.datastore;

import org.apache.parquet.hadoop.metadata.CompressionCodecName;

public class GzipTypedParquetOhlcStore extends TypedParquetOhlcStore {

    public GzipTypedParquetOhlcStore() {
        super(CompressionCodecName.GZIP, "Parquet-T+GZIP");
    }
}
