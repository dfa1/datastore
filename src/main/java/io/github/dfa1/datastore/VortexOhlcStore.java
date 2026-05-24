package io.github.dfa1.datastore;

import dev.vortex.api.DataSource;
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
import org.apache.arrow.vector.DecimalVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowReader;
import org.apache.arrow.vector.types.DateUnit;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

public class VortexOhlcStore implements OhlcStore {

    static {
        NativeLoader.loadJni();
    }

    private static final Schema SCHEMA = new Schema(List.of(
            Field.notNullable("date",   new ArrowType.Date(DateUnit.DAY)),
            Field.notNullable("symbol", ArrowType.Utf8.INSTANCE),
            Field.notNullable("open",   new ArrowType.Decimal(9, 2, 128)),
            Field.notNullable("high",   new ArrowType.Decimal(9, 2, 128)),
            Field.notNullable("low",    new ArrowType.Decimal(9, 2, 128)),
            Field.notNullable("close",  new ArrowType.Decimal(9, 2, 128)),
            Field.notNullable("volume", new ArrowType.Int(64, true))
    ));

    private final String formatName;
    private final HashMap<String, String> options;

    public VortexOhlcStore() {
        this("Vortex", new HashMap<>());
    }

    VortexOhlcStore(String formatName, HashMap<String, String> options) {
        this.formatName = formatName;
        this.options    = options;
    }

    @Override
    public String format() {
        return formatName;
    }

    private static final int BATCH_SIZE = 65_536;

    @Override
    public void write(Stream<OhlcRecord> records, Path path) throws IOException {
        BufferAllocator allocator = ArrowAllocation.rootAllocator();
        Session session = Session.create();
        String uri = path.toAbsolutePath().toUri().toString();

        try (VortexWriter writer = VortexWriter.create(session, uri, SCHEMA, options, allocator)) {
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
            DateDayVector  dateVec   = (DateDayVector)  root.getVector("date");
            VarCharVector  symbolVec = (VarCharVector)  root.getVector("symbol");
            DecimalVector  openVec   = (DecimalVector)  root.getVector("open");
            DecimalVector  highVec   = (DecimalVector)  root.getVector("high");
            DecimalVector  lowVec    = (DecimalVector)  root.getVector("low");
            DecimalVector  closeVec  = (DecimalVector)  root.getVector("close");
            BigIntVector   volumeVec = (BigIntVector)   root.getVector("volume");

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
                symbolVec.setSafe(i, r.symbol().getBytes(StandardCharsets.UTF_8));
                openVec.setSafe(i,   BigDecimal.valueOf(Math.round(r.open()  * 100), 2));
                highVec.setSafe(i,   BigDecimal.valueOf(Math.round(r.high()  * 100), 2));
                lowVec.setSafe(i,    BigDecimal.valueOf(Math.round(r.low()   * 100), 2));
                closeVec.setSafe(i,  BigDecimal.valueOf(Math.round(r.close() * 100), 2));
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
        BufferAllocator allocator = ArrowAllocation.rootAllocator();
        Session session = Session.create();
        String uri = path.toAbsolutePath().toUri().toString();

        var result = new ArrayList<OhlcRecord>();
        DataSource ds = DataSource.open(session, uri);
        Scan scan = ds.scan(ScanOptions.of());

        while (scan.hasNext()) {
            Partition partition = scan.next();
            try (ArrowReader reader = partition.scanArrow(allocator)) {
                while (reader.loadNextBatch()) {
                    VectorSchemaRoot root = reader.getVectorSchemaRoot();
                    DateDayVector  dateVec   = (DateDayVector)  root.getVector("date");
                    VarCharVector  symbolVec = (VarCharVector)  root.getVector("symbol");
                    DecimalVector  openVec   = (DecimalVector)  root.getVector("open");
                    DecimalVector  highVec   = (DecimalVector)  root.getVector("high");
                    DecimalVector  lowVec    = (DecimalVector)  root.getVector("low");
                    DecimalVector  closeVec  = (DecimalVector)  root.getVector("close");
                    BigIntVector   volumeVec = (BigIntVector)   root.getVector("volume");

                    for (int i = 0; i < root.getRowCount(); i++) {
                        result.add(new OhlcRecord(
                                LocalDate.ofEpochDay(dateVec.get(i)),
                                new String(symbolVec.get(i), StandardCharsets.UTF_8),
                                openVec.getObject(i).doubleValue(),
                                highVec.getObject(i).doubleValue(),
                                lowVec.getObject(i).doubleValue(),
                                closeVec.getObject(i).doubleValue(),
                                volumeVec.get(i)
                        ));
                    }
                }
            }
        }
        return result;
    }
}
