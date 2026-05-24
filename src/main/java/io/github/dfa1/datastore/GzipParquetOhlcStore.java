package io.github.dfa1.datastore;

import org.apache.parquet.hadoop.metadata.CompressionCodecName;

public class GzipParquetOhlcStore extends ParquetOhlcStore {

    public GzipParquetOhlcStore() {
        super(CompressionCodecName.GZIP, "Parquet+GZIP");
    }
}
