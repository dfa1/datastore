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

/**
 * Parquet store using native logical types:
 *  - date   → DATE (int32 days-since-epoch, delta-encoded by Parquet)
 *  - prices → DECIMAL backed by int32 (scale=2, i.e. price × 100)
 *  - symbol → STRING (dictionary-encoded automatically)
 *  - volume → int64
 *
 * Much smaller than the generic double/string approach, especially uncompressed.
 */
public class TypedParquetOhlcStore implements OhlcStore {

    // int32-backed DECIMAL: max representable price = 21_474_836.47 — well above any realistic equity price
    static final Schema SCHEMA = new Schema.Parser().parse("""
            {
              "type": "record",
              "name": "OhlcRecordTyped",
              "namespace": "io.github.dfa1.datastore",
              "fields": [
                {"name": "date",   "type": {"type": "int",  "logicalType": "date"}},
                {"name": "symbol", "type": "string"},
                {"name": "open",   "type": {"type": "int",  "logicalType": "decimal", "precision": 9, "scale": 2}},
                {"name": "high",   "type": {"type": "int",  "logicalType": "decimal", "precision": 9, "scale": 2}},
                {"name": "low",    "type": {"type": "int",  "logicalType": "decimal", "precision": 9, "scale": 2}},
                {"name": "close",  "type": {"type": "int",  "logicalType": "decimal", "precision": 9, "scale": 2}},
                {"name": "volume", "type": "long"}
              ]
            }
            """);

    private final CompressionCodecName codec;
    private final String               format;

    public TypedParquetOhlcStore() {
        this(CompressionCodecName.UNCOMPRESSED, "Parquet-T");
    }

    TypedParquetOhlcStore(CompressionCodecName codec, String format) {
        this.codec  = codec;
        this.format = format;
    }

    @Override
    public String format() {
        return format;
    }

    @Override
    public void write(List<OhlcRecord> records, Path path) throws IOException {
        try (var writer = AvroParquetWriter.<GenericRecord>builder(new NioOutputFile(path))
                .withSchema(SCHEMA)
                .withCompressionCodec(codec)
                .build()) {
            for (var r : records) {
                var rec = new GenericData.Record(SCHEMA);
                rec.put("date",   (int) r.date().toEpochDay());
                rec.put("symbol", r.symbol());
                rec.put("open",   (int) Math.round(r.open()  * 100));
                rec.put("high",   (int) Math.round(r.high()  * 100));
                rec.put("low",    (int) Math.round(r.low()   * 100));
                rec.put("close",  (int) Math.round(r.close() * 100));
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
                        (int) rec.get("open")  / 100.0,
                        (int) rec.get("high")  / 100.0,
                        (int) rec.get("low")   / 100.0,
                        (int) rec.get("close") / 100.0,
                        (long) rec.get("volume")
                ));
            }
        }
        return records;
    }
}
