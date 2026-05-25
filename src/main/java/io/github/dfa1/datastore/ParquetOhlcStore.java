package io.github.dfa1.datastore;

import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.api.InitContext;
import org.apache.parquet.hadoop.api.ReadSupport;
import org.apache.parquet.hadoop.api.WriteSupport;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.InputFile;
import org.apache.parquet.io.OutputFile;
import org.apache.parquet.io.api.*;
import org.apache.parquet.schema.*;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ParquetOhlcStore implements OhlcStore {

    static final MessageType SCHEMA = Types.buildMessage()
            .required(PrimitiveType.PrimitiveTypeName.INT32).as(LogicalTypeAnnotation.dateType()).named("date")
            .required(PrimitiveType.PrimitiveTypeName.BINARY).as(LogicalTypeAnnotation.stringType()).named("symbol")
            .required(PrimitiveType.PrimitiveTypeName.DOUBLE).named("open")
            .required(PrimitiveType.PrimitiveTypeName.DOUBLE).named("high")
            .required(PrimitiveType.PrimitiveTypeName.DOUBLE).named("low")
            .required(PrimitiveType.PrimitiveTypeName.DOUBLE).named("close")
            .required(PrimitiveType.PrimitiveTypeName.INT64).named("volume")
            .named("OhlcRecord");

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
        try (var writer = new OhlcWriterBuilder(new NioOutputFile(path)).withCompressionCodec(codec).build()) {
            var it = records.iterator();
            while (it.hasNext()) writer.write(it.next());
        }
    }

    @Override
    public List<OhlcRecord> read(Path path) throws IOException {
        var records = new ArrayList<OhlcRecord>();
        try (var reader = new InputFileReaderBuilder<>(new OhlcReadSupport(SCHEMA), new NioInputFile(path)).build()) {
            OhlcRecord rec;
            while ((rec = reader.read()) != null) records.add(rec);
        }
        return records;
    }

    @Override
    public double[] readColumn(Path path, NumericColumn column) throws IOException {
        MessageType projected = new MessageType("OhlcRecord", List.of(SCHEMA.getType(column.fieldName())));
        var values = new ArrayList<Double>();
        try (var reader = new InputFileReaderBuilder<>(new SingleColumnReadSupport(projected), new NioInputFile(path)).build()) {
            Double v;
            while ((v = reader.read()) != null) values.add(v);
        }
        double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
        return result;
    }

    // --- writer ---

    private static class OhlcWriterBuilder extends ParquetWriter.Builder<OhlcRecord, OhlcWriterBuilder> {
        OhlcWriterBuilder(OutputFile file) { super(file); }

        @Override protected OhlcWriterBuilder self() { return this; }

        @Override
        protected WriteSupport<OhlcRecord> getWriteSupport(Configuration conf) {
            return new OhlcWriteSupport();
        }
    }

    private static class OhlcWriteSupport extends WriteSupport<OhlcRecord> {
        private RecordConsumer consumer;

        @Override
        public WriteContext init(Configuration conf) {
            return new WriteContext(SCHEMA, Map.of());
        }

        @Override
        public void prepareForWrite(RecordConsumer consumer) {
            this.consumer = consumer;
        }

        @Override
        public void write(OhlcRecord r) {
            consumer.startMessage();
            consumer.startField("date",   0); consumer.addInteger((int) r.date().toEpochDay());         consumer.endField("date",   0);
            consumer.startField("symbol", 1); consumer.addBinary(Binary.fromString(r.symbol().value())); consumer.endField("symbol", 1);
            consumer.startField("open",   2); consumer.addDouble(r.open());                             consumer.endField("open",   2);
            consumer.startField("high",   3); consumer.addDouble(r.high());                             consumer.endField("high",   3);
            consumer.startField("low",    4); consumer.addDouble(r.low());                              consumer.endField("low",    4);
            consumer.startField("close",  5); consumer.addDouble(r.close());                            consumer.endField("close",  5);
            consumer.startField("volume", 6); consumer.addLong(r.volume());                             consumer.endField("volume", 6);
            consumer.endMessage();
        }
    }

    // --- reader builder (InputFile variant — protected constructor, must override getReadSupport) ---

    private static class InputFileReaderBuilder<T> extends ParquetReader.Builder<T> {
        private final ReadSupport<T> support;

        InputFileReaderBuilder(ReadSupport<T> support, InputFile file) {
            super(file);
            this.support = support;
        }

        @Override
        protected ReadSupport<T> getReadSupport() { return support; }
    }

    // --- reader (full record) ---

    private static class OhlcReadSupport extends ReadSupport<OhlcRecord> {
        private final MessageType schema;

        OhlcReadSupport(MessageType schema) { this.schema = schema; }

        @Override
        public ReadContext init(InitContext ctx) { return new ReadContext(schema); }

        @Override
        public RecordMaterializer<OhlcRecord> prepareForRead(Configuration conf,
                Map<String, String> metadata, MessageType fileSchema, ReadContext ctx) {
            return new OhlcRecordMaterializer();
        }
    }

    private static class OhlcRecordMaterializer extends RecordMaterializer<OhlcRecord> {
        private final OhlcGroupConverter root = new OhlcGroupConverter();

        @Override public OhlcRecord getCurrentRecord()     { return root.build(); }
        @Override public GroupConverter getRootConverter() { return root; }
    }

    private static class OhlcGroupConverter extends GroupConverter {
        private int    date;
        private String symbol;
        private double open, high, low, close;
        private long   volume;

        private final Converter[] converters = {
            new PrimitiveConverter() { @Override public void addInt(int v)       { date   = v; } },
            new PrimitiveConverter() { @Override public void addBinary(Binary v) { symbol = v.toStringUsingUTF8(); } },
            new PrimitiveConverter() { @Override public void addDouble(double v) { open   = v; } },
            new PrimitiveConverter() { @Override public void addDouble(double v) { high   = v; } },
            new PrimitiveConverter() { @Override public void addDouble(double v) { low    = v; } },
            new PrimitiveConverter() { @Override public void addDouble(double v) { close  = v; } },
            new PrimitiveConverter() { @Override public void addLong(long v)     { volume = v; } },
        };

        @Override public Converter getConverter(int i) { return converters[i]; }
        @Override public void start() {}
        @Override public void end()   {}

        OhlcRecord build() {
            return new OhlcRecord(LocalDate.ofEpochDay(date), new Symbol(symbol),
                    open, high, low, close, volume);
        }
    }

    // --- reader (single column projection) ---

    private static class SingleColumnReadSupport extends ReadSupport<Double> {
        private final MessageType projected;

        SingleColumnReadSupport(MessageType projected) { this.projected = projected; }

        @Override
        public ReadContext init(InitContext ctx) { return new ReadContext(projected); }

        @Override
        public RecordMaterializer<Double> prepareForRead(Configuration conf,
                Map<String, String> metadata, MessageType fileSchema, ReadContext ctx) {
            boolean isLong = projected.getFields().get(0).asPrimitiveType()
                    .getPrimitiveTypeName() == PrimitiveType.PrimitiveTypeName.INT64;
            return new SingleColumnMaterializer(isLong);
        }
    }

    private static class SingleColumnMaterializer extends RecordMaterializer<Double> {
        private double value;
        private final GroupConverter root;

        SingleColumnMaterializer(boolean isLong) {
            root = new GroupConverter() {
                private final PrimitiveConverter conv = isLong
                        ? new PrimitiveConverter() { @Override public void addLong(long v)     { value = (double) v; } }
                        : new PrimitiveConverter() { @Override public void addDouble(double v) { value = v; } };

                @Override public Converter getConverter(int i) { return conv; }
                @Override public void start() {}
                @Override public void end()   {}
            };
        }

        @Override public Double getCurrentRecord()        { return value; }
        @Override public GroupConverter getRootConverter() { return root; }
    }
}
