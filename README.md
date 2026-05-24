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

Sizes in bytes vs plain CSV. Times in milliseconds (2 warmup runs + 10 measured iterations via `System.nanoTime()`; columns show avg / min / max).
Scales: 252 ≈ 1 trading year; 2 520 ≈ 10 years; 10 k; 100 k.

### 252 records (1 year)

| Format       | Size (bytes) | vs CSV | Write ms (avg/min/max) | Read ms (avg/min/max) |
|--------------|-------------:|-------:|----------------------:|---------------------:|
| CSV          |       12,708 |  1.0×  |            0 / 0 / 3  |           0 / 0 / 2  |
| CSV+GZIP     |        4,451 |  0.4×  |            0 / 0 / 0  |           0 / 0 / 0  |
| CSV+ZSTD     |        9,085 |  0.7×  |            1 / 0 / 2  |           0 / 0 / 0  |
| JSON         |       27,537 |  2.2×  |            0 / 0 / 2  |           0 / 0 / 0  |
| JSON+GZIP    |        5,519 |  0.4×  |            0 / 0 / 0  |           0 / 0 / 0  |
| JSON+ZSTD    |        5,585 |  0.4×  |            0 / 0 / 0  |           0 / 0 / 1  |
| Parquet      |       13,039 |  1.0×  |            5 / 4 / 8  |           3 / 2 / 4  |
| Parquet+GZIP |        6,981 |  0.5×  |            5 / 4 / 8  |           3 / 2 / 5  |
| Parquet+ZSTD |        7,091 |  0.6×  |            4 / 4 / 5  |           2 / 2 / 3  |
| Vortex       |       18,916 |  1.5×  |            2 / 2 / 3  |           0 / 0 / 0  |

At small scale: CSV/JSON ops are sub-millisecond. Parquet pays per-call codec init (~4–5 ms).

### 2 520 records (10 years)

| Format       | Size (bytes) | vs CSV | Write ms (avg/min/max) | Read ms (avg/min/max) |
|--------------|-------------:|-------:|----------------------:|---------------------:|
| CSV          |      127,181 |  1.0×  |            3 / 3 / 4  |           1 / 1 / 4  |
| CSV+GZIP     |       43,595 |  0.3×  |            6 / 6 / 6  |           1 / 1 / 1  |
| CSV+ZSTD     |       86,116 |  0.7×  |            5 / 5 / 6  |           1 / 1 / 1  |
| JSON         |      275,822 |  2.2×  |            1 / 1 / 2  |           1 / 1 / 2  |
| JSON+GZIP    |       53,637 |  0.4×  |            4 / 4 / 4  |           1 / 1 / 1  |
| JSON+ZSTD    |       54,408 |  0.4×  |            1 / 1 / 1  |           1 / 1 / 1  |
| Parquet      |      112,874 |  0.9×  |            5 / 5 / 6  |           3 / 3 / 3  |
| Parquet+GZIP |       43,755 |  0.3×  |            8 / 7 / 9  |           3 / 2 / 4  |
| Parquet+ZSTD |       46,089 |  0.4×  |            4 / 4 / 5  |           2 / 2 / 3  |
| Vortex       |       43,276 |  0.3×  |            3 / 3 / 4  |           0 / 0 / 1  |

Vortex reaches CSV+GZIP size parity with faster reads and no explicit compression step.

### 10 000 records

| Format       | Size (bytes) | vs CSV | Write ms (avg/min/max) | Read ms (avg/min/max) |
|--------------|-------------:|-------:|----------------------:|---------------------:|
| CSV          |      484,118 |  1.0×  |          13 / 13 / 17  |           4 / 4 / 4  |
| CSV+GZIP     |      163,079 |  0.3×  |          23 / 23 / 24  |           5 / 5 / 5  |
| CSV+ZSTD     |      322,420 |  0.7×  |          22 / 22 / 25  |           5 / 5 / 5  |
| JSON         |    1,074,079 |  2.2×  |            3 / 3 / 6  |           4 / 4 / 4  |
| JSON+GZIP    |      202,119 |  0.4×  |          17 / 17 / 18  |           4 / 4 / 5  |
| JSON+ZSTD    |      209,980 |  0.4×  |            4 / 4 / 5  |           4 / 4 / 5  |
| Parquet      |      384,085 |  0.8×  |            8 / 8 / 10  |           4 / 3 / 5  |
| Parquet+GZIP |      185,250 |  0.4×  |          18 / 18 / 19  |           4 / 4 / 4  |
| Parquet+ZSTD |      197,340 |  0.4×  |            8 / 8 / 9  |           4 / 4 / 4  |
| Vortex       |      130,860 |  0.3×  |            6 / 6 / 8  |           0 / 0 / 1  |

Vortex smallest and fastest to read; Parquet+ZSTD best write/size tradeoff among compressed formats.

### 100 000 records

| Format       | Size (bytes) | vs CSV | Write ms (avg/min/max) | Read ms (avg/min/max) |
|--------------|-------------:|-------:|----------------------:|---------------------:|
| CSV          |    4,638,435 |  1.0×  |        131 / 128 / 134  |         45 / 43 / 48  |
| CSV+GZIP     |    1,291,129 |  0.3×  |        189 / 187 / 201  |         50 / 49 / 54  |
| CSV+ZSTD     |    2,753,718 |  0.6×  |        228 / 222 / 242  |         51 / 48 / 56  |
| JSON         |   10,538,396 |  2.3×  |          41 / 34 / 60  |         53 / 42 / 77  |
| JSON+GZIP    |    1,572,003 |  0.3×  |        138 / 137 / 142  |         46 / 46 / 49  |
| JSON+ZSTD    |    1,693,655 |  0.4×  |          45 / 45 / 48  |         43 / 43 / 45  |
| Parquet      |    2,560,131 |  0.6×  |          36 / 35 / 39  |         17 / 17 / 18  |
| Parquet+GZIP |    1,266,857 |  0.3×  |        125 / 122 / 129  |         21 / 20 / 24  |
| Parquet+ZSTD |    1,417,150 |  0.3×  |          40 / 40 / 42  |         19 / 19 / 22  |
| Vortex       |    1,453,420 |  0.3×  |          21 / 21 / 22  |           4 / 4 / 5  |

Parquet+GZIP is smallest. Vortex reads in 4 ms — 5× faster than any Parquet variant. JSON shows high variance (34–60 ms write, 42–77 ms read) from GC pressure at this scale.

## Key findings

1. **JSON is always the worst for size** — 2.2–2.3× CSV before compression, never competitive.
2. **Plain CSV beats plain Parquet only at tiny scale** (<252 records); at 10 k records Parquet is 20% smaller uncompressed.
3. **Vortex breaks even with compressed formats around 2 500 records** — with zero explicit compression step.
4. **Parquet+GZIP is smallest at 100 k** (1.27 MB), but Vortex reads 5× faster (4 ms vs 19–21 ms).
5. **ZSTD lags GZIP on size** — OHLC data (smooth prices, repeated symbol) suits GZIP's sliding-window; ZSTD dictionary advantage not triggered here.
6. **CSV+ZSTD write is slow** — streaming ZSTD without dictionary shows high overhead at all scales; single-shot compression would help.
7. **JSON variance grows with scale** — GC pressure from string allocation causes 2× spread between min/max at 100 k records.

## Running

```bash
# verify all formats produce identical data
./mvnw test -Dtest=CrossFormatConsistencyTest

# storage + timing benchmark (prints tables to stdout)
./mvnw test -Dtest=BenchmarkTest
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
