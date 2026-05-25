# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# run all tests
./mvnw test

# run a single test class
./mvnw test -Dtest=CsvOhlcStoreTest

# run the benchmark (prints tables to stdout)
./mvnw test -Dtest=BenchmarkTest

# verify cross-format correctness
./mvnw test -Dtest=CrossFormatConsistencyTest
```

Surefire is pinned to GraalVM JDK 25 (`/Library/Java/JavaVirtualMachines/graalvm-25.jdk`). Tests require `--enable-native-access=ALL-UNNAMED` for Vortex JNI. Do not remove these surefire flags.

## Architecture

**Core abstraction:** `OhlcStore` — one interface, three methods: `write`, `read`, `readColumn`. Ten implementations cover every format/compression combination. Each implementation owns its `StoreType` enum value.

**Store hierarchy:**
- CSV/JSON stores delegate to Jackson (`jackson-dataformat-csv`, `jackson-databind`)
- Parquet stores use Avro schema + `AvroParquetWriter/Reader` via `NioOutputFile`/`NioInputFile` adapters
- `GzipParquetStore` / `ZstdParquetStore` extend `ParquetOhlcStore` by passing a different `CompressionCodecName`
- `VortexOhlcStore` is standalone — uses Arrow `VectorSchemaRoot` batches (65 536 rows) written via JNI; reads via `DataSource` → `Scan` → `Partition` → `ArrowReader`

**`readColumn`:** Default implementation in `OhlcStore` does a full `read()` then extracts. `VortexOhlcStore` and `ParquetOhlcStore` override it with projection pushdown (Vortex uses `Expression.select`; Parquet uses `AvroReadSupport.AVRO_REQUESTED_PROJECTION`).

**Data model:** `OhlcRecord` is a Java record. `Symbol` wraps a string ticker. `NumericColumn` enum maps field names to `OhlcRecord` accessors — `fieldName()` returns the lowercase enum name, which must match the Avro/Arrow schema field names exactly.

**Tests:** Every store has a `*OhlcStoreTest` extending `AbstractOhlcStoreTest` (round-trip + storeType checks). `BenchmarkTest` runs 2 warmup + 10 measured iterations across 7 scales (1y–50y trading days). `CrossFormatConsistencyTest` asserts all 10 stores produce identical records for the same input.

**Known issue:** Vortex `Session` has no `close()` API (upstream bug vortex-data/vortex#8075, crashes on JDK 26). Surefire is pinned to JDK 25 as a workaround.

## Testing

1. Write unit tests in JUnit 5 using `@TempDir`.
2. **Always** follow the `// Given / // When / // Then` structure — every test, no exceptions.
   - `// Given` sets up state.
   - `// When` performs the action under test — **never combine with `// Then`**. Extract the result into a local variable:
     ```java
     // When
     var result = store.read(file);

     // Then
     assertEquals(expected, result);
     ```
   - `// Then` asserts the outcome. The assertion always operates on the variable captured in `// When`, never inline.
   - For void actions (`write`, `flush`, …) there is no return value to capture; just place the call under `// When` and put assertions (if any) under `// Then`.
   - For tests with no meaningful setup, use `// Given` with a blank line or a comment explaining why there is none.
3. **Run tests:** `./mvnw test`
