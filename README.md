# datastore

Comparing OHLC storage formats in Java 25: CSV, JSON, Parquet, and Vortex — with GZIP and ZSTD compression variants.

## Why

Financial time-series (OHLC: date, symbol, open, high, low, close, volume) is simple, repetitive, and write-once/read-many. Choosing the right format matters:

- **CSV** — universal, human-readable, no schema, large
- **JSON** — self-describing, ~2× larger than CSV, poor fit for columnar data
- **Parquet** — columnar, typed, compresses well at scale
- **Vortex** — columnar with built-in encoding (no separate compressor needed at scale)

The question: at what scale does each format win, and how much does compression help?

## Data model

```java
record OhlcRecord(LocalDate date, String symbol,
                  double open, double high, double low, double close,
                  long volume)
```

All stores write/read identical `OhlcRecord` values. Correctness is verified by `CrossFormatConsistencyTest` (1 000-record round-trip across all 10 formats).

## Benchmark

Sizes in bytes vs plain CSV. Times in milliseconds (wall-clock, single run, JVM warmed by smaller scales first).
Scales: 252 ≈ 1 trading year; 2 520 ≈ 10 years; 10 k; 100 k.

### 252 records (1 year)

| Format       | Size (bytes) | vs CSV | Write ms | Read ms |
|--------------|-------------:|-------:|---------:|--------:|
| CSV          |       12,708 |  1.0×  |       15 |      12 |
| CSV+GZIP     |        4,451 |  0.4×  |        5 |       3 |
| CSV+ZSTD     |        9,085 |  0.7×  |      162 |       3 |
| JSON         |       27,537 |  2.2×  |        4 |       4 |
| JSON+GZIP    |        5,519 |  0.4×  |        3 |       2 |
| JSON+ZSTD    |        5,585 |  0.4×  |        2 |       3 |
| Parquet      |       13,039 |  1.0×  |      212 |      24 |
| Parquet+GZIP |        6,981 |  0.5×  |       18 |       5 |
| Parquet+ZSTD |        7,091 |  0.6×  |       10 |       5 |
| Vortex       |       18,916 |  1.5×  |      293 |       7 |

At small scale: Vortex and plain Parquet pay JNI/codec init overhead on first write; times settle at larger scales.

### 2 520 records (10 years)

| Format       | Size (bytes) | vs CSV | Write ms | Read ms |
|--------------|-------------:|-------:|---------:|--------:|
| CSV          |      127,181 |  1.0×  |       11 |       5 |
| CSV+GZIP     |       43,595 |  0.3×  |        9 |       3 |
| CSV+ZSTD     |       86,116 |  0.7×  |        8 |       3 |
| JSON         |      275,822 |  2.2×  |        6 |       4 |
| JSON+GZIP    |       53,637 |  0.4×  |        6 |       4 |
| JSON+ZSTD    |       54,408 |  0.4×  |        3 |       3 |
| Parquet      |      112,874 |  0.9×  |       16 |       6 |
| Parquet+GZIP |       43,755 |  0.3×  |       16 |       5 |
| Parquet+ZSTD |       46,089 |  0.4×  |       10 |       5 |
| Vortex       |       43,276 |  0.3×  |        8 |       2 |

Vortex reaches CSV+GZIP size parity and already leads on read speed.

### 10 000 records

| Format       | Size (bytes) | vs CSV | Write ms | Read ms |
|--------------|-------------:|-------:|---------:|--------:|
| CSV          |      484,118 |  1.0×  |       16 |       9 |
| CSV+GZIP     |      163,079 |  0.3×  |       27 |       8 |
| CSV+ZSTD     |      322,420 |  0.7×  |       26 |       6 |
| JSON         |    1,074,079 |  2.2×  |        7 |       7 |
| JSON+GZIP    |      202,119 |  0.4×  |       20 |       7 |
| JSON+ZSTD    |      209,980 |  0.4×  |        6 |       6 |
| Parquet      |      384,085 |  0.8×  |       24 |       9 |
| Parquet+GZIP |      185,250 |  0.4×  |       30 |       9 |
| Parquet+ZSTD |      197,340 |  0.4×  |       16 |       8 |
| Vortex       |      130,860 |  0.3×  |       11 |       2 |

Vortex is smallest and fastest to read.

### 100 000 records

| Format       | Size (bytes) | vs CSV | Write ms | Read ms |
|--------------|-------------:|-------:|---------:|--------:|
| CSV          |    4,638,435 |  1.0×  |      135 |      47 |
| CSV+GZIP     |    1,291,129 |  0.3×  |      187 |      49 |
| CSV+ZSTD     |    2,753,718 |  0.6×  |      232 |      47 |
| JSON         |   10,538,396 |  2.3×  |       34 |      47 |
| JSON+GZIP    |    1,572,003 |  0.3×  |      137 |      49 |
| JSON+ZSTD    |    1,693,655 |  0.4×  |       47 |      46 |
| Parquet      |    2,560,131 |  0.6×  |       59 |      26 |
| Parquet+GZIP |    1,266,857 |  0.3×  |      128 |      25 |
| Parquet+ZSTD |    1,417,150 |  0.3×  |       44 |      22 |
| Vortex       |    1,453,420 |  0.3×  |       40 |      11 |

Parquet+GZIP smallest. Vortex read 11 ms — 2× faster than any Parquet variant.

## Key findings

1. **JSON is always the worst for size** — 2.2–2.3× CSV before compression, never competitive.
2. **Plain CSV beats plain Parquet only at tiny scale** (<252 records); at 10 k records Parquet is 20% smaller uncompressed.
3. **Vortex breaks even with compressed formats around 2 500 records** — with zero explicit compression step.
4. **Parquet+GZIP is smallest at 100 k** (1.27 MB), but Vortex reads 2× faster (11 ms vs 22–25 ms).
5. **ZSTD lags GZIP on size** — OHLC data (smooth prices, repeated symbol) suits GZIP's sliding-window; ZSTD dictionary advantage not triggered here.
6. **CSV+ZSTD write is slow** — streaming ZSTD without dictionary shows high overhead at all scales; single-shot compression would help.

## Running

```bash
# verify all formats produce identical data
mvn test -Dtest=CrossFormatConsistencyTest

# storage + timing benchmark (prints tables to stdout)
mvn test -Dtest=BenchmarkTest
```

## Stack

| Library            | Version      | Role                      |
|--------------------|--------------|---------------------------|
| Jackson CSV / JSON | 2.18.3       | CSV + JSON I/O            |
| Parquet + Hadoop   | 1.15.0 / 3.4.1 | Parquet read/write      |
| Apache Arrow       | 19.0.0       | Vortex in-memory columnar |
| Vortex JNI         | 0.72.0       | Vortex file format        |
| zstd-jni           | 1.5.6-3      | ZSTD compression          |
| JUnit 5            | 5.11.4       | Tests                     |
| Java               | 25           | Runtime                   |
