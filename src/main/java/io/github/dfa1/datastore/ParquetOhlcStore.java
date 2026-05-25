package io.github.dfa1.datastore;

import org.apache.parquet.ParquetReadOptions;
import org.apache.parquet.bytes.HeapByteBufferAllocator;
import org.apache.parquet.column.ColumnWriteStore;
import org.apache.parquet.column.ParquetProperties;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.compression.CompressionCodecFactory.BytesInputCompressor;
import org.apache.parquet.hadoop.ColumnChunkPageWriteStore;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.ParquetFileWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.ColumnIOFactory;
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

    private static final long ROW_GROUP_SIZE = 128 * 1024 * 1024L;
    private static final int  MAX_PADDING    =   8 * 1024 * 1024;

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
        var props      = ParquetProperties.builder().build();
        var compressor = Codecs.compressor(codec);
        var allocator  = HeapByteBufferAllocator.getInstance();

        var fileWriter = new ParquetFileWriter(new NioOutputFile(path), SCHEMA,
                ParquetFileWriter.Mode.CREATE, ROW_GROUP_SIZE, MAX_PADDING);
        fileWriter.start();

        var pageStore = newPageStore(compressor, props, allocator);
        var colStore  = props.newColumnWriteStore(SCHEMA, pageStore, pageStore);
        var consumer  = new ColumnIOFactory().getColumnIO(SCHEMA).getRecordWriter(colStore);
        long count    = 0;

        for (var it = records.iterator(); it.hasNext(); ) {
            OhlcRecord r = it.next();
            consumer.startMessage();
            consumer.startField("date",   0); consumer.addInteger((int) r.date().toEpochDay());          consumer.endField("date",   0);
            consumer.startField("symbol", 1); consumer.addBinary(Binary.fromString(r.symbol().value()));  consumer.endField("symbol", 1);
            consumer.startField("open",   2); consumer.addDouble(r.open());                              consumer.endField("open",   2);
            consumer.startField("high",   3); consumer.addDouble(r.high());                              consumer.endField("high",   3);
            consumer.startField("low",    4); consumer.addDouble(r.low());                               consumer.endField("low",    4);
            consumer.startField("close",  5); consumer.addDouble(r.close());                             consumer.endField("close",  5);
            consumer.startField("volume", 6); consumer.addLong(r.volume());                              consumer.endField("volume", 6);
            consumer.endMessage();
            count++;

            if (colStore.getBufferedSize() > ROW_GROUP_SIZE) {
                flush(fileWriter, colStore, pageStore, count);
                count     = 0;
                pageStore = newPageStore(compressor, props, allocator);
                colStore  = props.newColumnWriteStore(SCHEMA, pageStore, pageStore);
                consumer  = new ColumnIOFactory().getColumnIO(SCHEMA).getRecordWriter(colStore);
            }
        }

        if (count > 0) flush(fileWriter, colStore, pageStore, count);
        fileWriter.end(Map.of());
        compressor.release();
    }

    private static void flush(ParquetFileWriter fw, ColumnWriteStore cs,
                               ColumnChunkPageWriteStore ps, long count) throws IOException {
        fw.startBlock(count);
        cs.flush();
        ps.flushToFileWriter(fw);
        fw.endBlock();
    }

    private static ColumnChunkPageWriteStore newPageStore(BytesInputCompressor compressor,
            ParquetProperties props, HeapByteBufferAllocator allocator) throws IOException {
        return new ColumnChunkPageWriteStore(compressor, SCHEMA, allocator,
                props.getColumnIndexTruncateLength(), props.getPageWriteChecksumEnabled());
    }

    @Override
    public List<OhlcRecord> read(Path path) throws IOException {
        var records = new ArrayList<OhlcRecord>();
        var options = ParquetReadOptions.builder().withCodecFactory(Codecs.factory()).build();
        try (var reader = ParquetFileReader.open(new NioInputFile(path), options)) {
            var columnIO = new ColumnIOFactory().getColumnIO(SCHEMA,
                    reader.getFooter().getFileMetaData().getSchema());
            PageReadStore rowGroup;
            while ((rowGroup = reader.readNextRowGroup()) != null) {
                var rr    = columnIO.getRecordReader(rowGroup, new OhlcRecordMaterializer());
                long rows = rowGroup.getRowCount();
                for (long i = 0; i < rows; i++) records.add(rr.read());
            }
        }
        return records;
    }

    @Override
    public double[] readColumn(Path path, NumericColumn column) throws IOException {
        MessageType projected = new MessageType("OhlcRecord", List.of(SCHEMA.getType(column.fieldName())));
        boolean isLong = SCHEMA.getType(column.fieldName()).asPrimitiveType()
                .getPrimitiveTypeName() == PrimitiveType.PrimitiveTypeName.INT64;
        var values  = new ArrayList<Double>();
        var options = ParquetReadOptions.builder().withCodecFactory(Codecs.factory()).build();
        try (var reader = ParquetFileReader.open(new NioInputFile(path), options)) {
            reader.setRequestedSchema(projected);
            var columnIO = new ColumnIOFactory().getColumnIO(projected,
                    reader.getFooter().getFileMetaData().getSchema());
            PageReadStore rowGroup;
            while ((rowGroup = reader.readNextRowGroup()) != null) {
                var rr    = columnIO.getRecordReader(rowGroup, new SingleColumnMaterializer(isLong));
                long rows = rowGroup.getRowCount();
                for (long i = 0; i < rows; i++) values.add(rr.read());
            }
        }
        double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
        return result;
    }

    // --- record materializer (full record) ---

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

    // --- record materializer (single column) ---

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
