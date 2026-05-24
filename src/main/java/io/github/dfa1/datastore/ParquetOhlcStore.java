package io.github.dfa1.datastore;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class ParquetOhlcStore implements OhlcStore {

    static final Schema SCHEMA = new Schema.Parser().parse("""
            {
              "type": "record",
              "name": "OhlcRecord",
              "namespace": "io.github.dfa1.datastore",
              "fields": [
                {"name": "date",   "type": {"type": "int", "logicalType": "date"}},
                {"name": "symbol", "type": "string"},
                {"name": "open",   "type": "double"},
                {"name": "high",   "type": "double"},
                {"name": "low",    "type": "double"},
                {"name": "close",  "type": "double"},
                {"name": "volume", "type": "long"}
              ]
            }
            """);

    private final CompressionCodecName codec;
    private final StoreType            type;

    public ParquetOhlcStore() {
        this(CompressionCodecName.UNCOMPRESSED, StoreType.PARQUET);
    }

    ParquetOhlcStore(CompressionCodecName codec, StoreType type) {
        this.codec = codec;
        this.type  = type;
    }

    @Override
    public StoreType storeType() { return type; }

    @Override
    public void write(Stream<OhlcRecord> records, Path path) throws IOException {
        try (var writer = AvroParquetWriter.<GenericRecord>builder(new NioOutputFile(path))
                .withSchema(SCHEMA)
                .withCompressionCodec(codec)
                .build()) {
            var it = records.iterator();
            while (it.hasNext()) {
                var r   = it.next();
                var rec = new GenericData.Record(SCHEMA);
                rec.put("date",   (int) r.date().toEpochDay());
                rec.put("symbol", r.symbol());
                rec.put("open",   r.open());
                rec.put("high",   r.high());
                rec.put("low",    r.low());
                rec.put("close",  r.close());
                rec.put("volume", r.volume());
                writer.write(rec);
            }
        }
    }

    @Override
    public List<OhlcRecord> read(Path path) throws IOException {
        var records = new ArrayList<OhlcRecord>();
        try (var reader = AvroParquetReader.<GenericRecord>builder(new NioInputFile(path))
                .withDataModel(GenericData.get())
                .build()) {
            GenericRecord rec;
            while ((rec = reader.read()) != null) {
                records.add(new OhlcRecord(
                        LocalDate.ofEpochDay((int) rec.get("date")),
                        rec.get("symbol").toString(),
                        (double) rec.get("open"),
                        (double) rec.get("high"),
                        (double) rec.get("low"),
                        (double) rec.get("close"),
                        (long) rec.get("volume")
                ));
            }
        }
        return records;
    }
}
