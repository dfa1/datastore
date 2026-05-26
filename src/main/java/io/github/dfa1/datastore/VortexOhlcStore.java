package io.github.dfa1.datastore;

import dev.vortex.api.DataSource;
import dev.vortex.api.Expression;
import dev.vortex.api.Partition;
import dev.vortex.api.Scan;
import dev.vortex.api.ScanOptions;
import dev.vortex.api.Session;
import dev.vortex.api.VortexWriter;
import dev.vortex.arrow.ArrowAllocation;
import dev.vortex.jni.NativeLoader;
import org.apache.arrow.c.ArrowArray;
import org.apache.arrow.c.ArrowSchema;
import org.apache.arrow.c.Data;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.DateDayVector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowReader;
import org.apache.arrow.vector.types.DateUnit;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.stream.DoubleStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class VortexOhlcStore implements OhlcStore {

    static {
        NativeLoader.loadJni();
    }

    /**
     * Shared Vortex session for the lifetime of the JVM.
     *
     * <p><strong>URI cache behaviour (upstream design):</strong> the session caches file
     * footers and metadata keyed by URI. This is intentional: the primary deployment
     * target is object storage (e.g. S3) where files are immutable once written.
     * Consequence: if you write a Vortex file, read it, then overwrite the <em>same
     * path</em> with different data and attempt to read again within the same session,
     * the session returns stale cached metadata and the read fails with
     * {@code [errno 22] SerializedArray buffer is too short for flatbuffer}.
     *
     * <p><strong>Workaround:</strong> always write to a fresh, unique path. Tests must
     * never reuse the same file path across write–read cycles; {@code @TempDir} with
     * a per-test unique filename satisfies this constraint automatically.
     */
    private static final Session   SESSION = Session.create();
    private static final ArrowType F64     = new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE);

    private static final Schema SCHEMA = new Schema(List.of(
            Field.notNullable("date",   new ArrowType.Date(DateUnit.DAY)),
            Field.notNullable("symbol", ArrowType.Utf8.INSTANCE),
            Field.notNullable("open",   F64),
            Field.notNullable("high",   F64),
            Field.notNullable("low",    F64),
            Field.notNullable("close",  F64),
            Field.notNullable("volume", new ArrowType.Int(64, true))
    ));

    private final StoreType           type;
    private final Map<String, String> options;
    private final BufferAllocator     allocator;

    public VortexOhlcStore() {
        this(StoreType.VORTEX, new HashMap<>());
    }

    VortexOhlcStore(StoreType type, Map<String, String> options) {
        this.type      = type;
        this.options   = options;
        this.allocator = ArrowAllocation.rootAllocator();
    }

    @Override
    public StoreType storeType() { return type; }

    private static final int BATCH_SIZE = 65_536;

    @Override
    public void write(Stream<OhlcRecord> records, Path path) throws IOException {
        String uri = path.toAbsolutePath().toUri().toString();
        try (VortexWriter writer = VortexWriter.create(SESSION, uri, SCHEMA, options, allocator)) {
            var batch = new ArrayList<OhlcRecord>(BATCH_SIZE);
            var it    = records.iterator();
            while (it.hasNext()) {
                batch.add(it.next());
                if (batch.size() == BATCH_SIZE) {
                    flush(writer, batch, allocator);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                flush(writer, batch, allocator);
            }
        }
    }

    private static void flush(VortexWriter writer, List<OhlcRecord> batch,
                              BufferAllocator allocator) throws IOException {
        try (VectorSchemaRoot root = VectorSchemaRoot.create(SCHEMA, allocator)) {
            DateDayVector dateVec   = (DateDayVector) root.getVector("date");
            VarCharVector symbolVec = (VarCharVector) root.getVector("symbol");
            Float8Vector  openVec   = (Float8Vector)  root.getVector("open");
            Float8Vector  highVec   = (Float8Vector)  root.getVector("high");
            Float8Vector  lowVec    = (Float8Vector)  root.getVector("low");
            Float8Vector  closeVec  = (Float8Vector)  root.getVector("close");
            BigIntVector  volumeVec = (BigIntVector)  root.getVector("volume");

            int n = batch.size();
            dateVec.allocateNew(n);
            symbolVec.allocateNew(n);
            openVec.allocateNew(n);
            highVec.allocateNew(n);
            lowVec.allocateNew(n);
            closeVec.allocateNew(n);
            volumeVec.allocateNew(n);

            for (int i = 0; i < n; i++) {
                var r = batch.get(i);
                dateVec.setSafe(i,   (int) r.date().toEpochDay());
                symbolVec.setSafe(i, r.symbol().value().getBytes(StandardCharsets.UTF_8));
                openVec.setSafe(i,   r.open());
                highVec.setSafe(i,   r.high());
                lowVec.setSafe(i,    r.low());
                closeVec.setSafe(i,  r.close());
                volumeVec.setSafe(i, r.volume());
            }

            root.setRowCount(n);
            try (ArrowArray  arr    = ArrowArray.allocateNew(allocator);
                 ArrowSchema schema = ArrowSchema.allocateNew(allocator)) {
                Data.exportVectorSchemaRoot(allocator, root, null, arr, schema);
                writer.writeBatch(arr.memoryAddress(), schema.memoryAddress());
            }
        }
    }

    @Override
    public List<OhlcRecord> read(Path path) throws IOException {
        String uri = path.toAbsolutePath().toUri().toString();

        var result = new ArrayList<OhlcRecord>();
        DataSource ds = DataSource.open(SESSION, uri);
        Scan scan = ds.scan(ScanOptions.of());
        while (scan.hasNext()) {
            Partition partition = scan.next();
            try (ArrowReader reader = partition.scanArrow(allocator)) {
                while (reader.loadNextBatch()) {
                    VectorSchemaRoot root = reader.getVectorSchemaRoot();
                    DateDayVector dateVec   = (DateDayVector) root.getVector("date");
                    VarCharVector symbolVec = (VarCharVector) root.getVector("symbol");
                    Float8Vector  openVec   = (Float8Vector)  root.getVector("open");
                    Float8Vector  highVec   = (Float8Vector)  root.getVector("high");
                    Float8Vector  lowVec    = (Float8Vector)  root.getVector("low");
                    Float8Vector  closeVec  = (Float8Vector)  root.getVector("close");
                    BigIntVector  volumeVec = (BigIntVector)  root.getVector("volume");
                    for (int i = 0; i < root.getRowCount(); i++) {
                        result.add(new OhlcRecord(
                                LocalDate.ofEpochDay(dateVec.get(i)),
                                new Symbol(new String(symbolVec.get(i), StandardCharsets.UTF_8)),
                                openVec.get(i), highVec.get(i), lowVec.get(i),
                                closeVec.get(i), volumeVec.get(i)
                        ));
                    }
                }
            }
        }
        return result;
    }

    @Override
    public double[] readColumn(Path path, NumericColumn column) throws IOException {
        String uri = path.toAbsolutePath().toUri().toString();
        ScanOptions opts = ScanOptions.builder()
                .projection(Expression.select(new String[]{column.fieldName()}, Expression.root()))
                .build();

        DoubleStream.Builder builder = DoubleStream.builder();
        DataSource ds   = DataSource.open(SESSION, uri);
        Scan       scan = ds.scan(opts);
        while (scan.hasNext()) {
            Partition partition = scan.next();
            try (ArrowReader reader = partition.scanArrow(allocator)) {
                while (reader.loadNextBatch()) {
                    VectorSchemaRoot root = reader.getVectorSchemaRoot();
                    if (column == NumericColumn.VOLUME) {
                        BigIntVector vec = (BigIntVector) root.getVector(column.fieldName());
                        for (int i = 0; i < root.getRowCount(); i++) builder.accept((double) vec.get(i));
                    } else {
                        Float8Vector vec = (Float8Vector) root.getVector(column.fieldName());
                        for (int i = 0; i < root.getRowCount(); i++) builder.accept(vec.get(i));
                    }
                }
            }
        }
        return builder.build().toArray();
    }
}
