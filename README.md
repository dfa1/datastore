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

## Storage benchmark

Sizes in bytes, ratio vs plain CSV. Scales: 252 ≈ 1 trading year; 2 520 ≈ 10 years; 10 k; 100 k.

### 252 records (1 year)

| Format       | Size (bytes) | vs CSV |
|--------------|-------------:|-------:|
| CSV          |       12,708 |  1.0×  |
| CSV+GZIP     |        4,451 |  0.4×  |
| CSV+ZSTD     |        9,085 |  0.7×  |
| JSON         |       27,537 |  2.2×  |
| JSON+GZIP    |        5,519 |  0.4×  |
| JSON+ZSTD    |        5,585 |  0.4×  |
| Parquet      |       13,039 |  1.0×  |
| Parquet+GZIP |        6,981 |  0.5×  |
| Parquet+ZSTD |        7,091 |  0.6×  |
| Vortex       |       18,916 |  1.5×  |

At small scale Vortex pays overhead; GZIP/ZSTD on CSV or JSON wins.

### 2 520 records (10 years)

| Format       | Size (bytes) | vs CSV |
|--------------|-------------:|-------:|
| CSV          |      127,181 |  1.0×  |
| CSV+GZIP     |       43,595 |  0.3×  |
| CSV+ZSTD     |       86,116 |  0.7×  |
| JSON         |      275,822 |  2.2×  |
| JSON+GZIP    |       53,637 |  0.4×  |
| JSON+ZSTD    |       54,408 |  0.4×  |
| Parquet      |      112,874 |  0.9×  |
| Parquet+GZIP |       43,755 |  0.3×  |
| Parquet+ZSTD |       46,089 |  0.4×  |
| Vortex       |       43,276 |  0.3×  |

Break-even: Vortex reaches CSV+GZIP parity around 10 years of data.

### 10 000 records

| Format       | Size (bytes) | vs CSV |
|--------------|-------------:|-------:|
| CSV          |      484,118 |  1.0×  |
| CSV+GZIP     |      163,079 |  0.3×  |
| CSV+ZSTD     |      322,420 |  0.7×  |
| JSON         |    1,074,079 |  2.2×  |
| JSON+GZIP    |      202,119 |  0.4×  |
| JSON+ZSTD    |      209,980 |  0.4×  |
| Parquet      |      384,085 |  0.8×  |
| Parquet+GZIP |      185,250 |  0.4×  |
| Parquet+ZSTD |      197,340 |  0.4×  |
| Vortex       |      130,860 |  0.3×  |

Vortex pulls ahead of CSV+GZIP at 10 k records.

### 100 000 records

| Format       |  Size (bytes) | vs CSV |
|--------------|:-------------:|-------:|
| CSV          |   4,638,435   |  1.0×  |
| CSV+GZIP     |   1,291,129   |  0.3×  |
| CSV+ZSTD     |   2,753,718   |  0.6×  |
| JSON         |  10,538,396   |  2.3×  |
| JSON+GZIP    |   1,572,003   |  0.3×  |
| JSON+ZSTD    |   1,693,655   |  0.4×  |
| Parquet      |   2,560,131   |  0.6×  |
| Parquet+GZIP |   1,266,857   |  0.3×  |
| Parquet+ZSTD |   1,417,150   |  0.3×  |
| Vortex       |   1,453,420   |  0.3×  |

At scale, **Parquet+GZIP is the smallest** (1.27 MB). Vortex and Parquet+ZSTD are close behind at 0.3×.

## Key findings

1. **JSON is always the worst** — 2.2–2.3× CSV even before compression, never competitive.
2. **Plain CSV beats plain Parquet only at tiny scale** (<252 records); at 10 k records Parquet is 20% smaller uncompressed.
3. **Vortex breaks even with compressed formats around 2 500 records** and beats CSV+GZIP above 10 k records — with zero explicit compression step.
4. **Parquet+GZIP wins at 100 k**: smallest absolute size, typed schema, columnar layout.
5. **ZSTD lags GZIP here** — OHLC data (smooth prices, repeated symbol) compresses well with GZIP's sliding-window model; ZSTD's dictionary advantage is not triggered at these scales.

## Running

```bash
# verify all formats produce identical data
mvn test -Dtest=CrossFormatConsistencyTest

# storage benchmark (prints tables to stdout)
mvn test -Dtest=BenchmarkTest
```

## Stack

| Library | Version | Role |
|---------|---------|------|
| Jackson CSV / JSON | 2.18.3 | CSV + JSON I/O |
| Parquet + Hadoop | 1.15.0 / 3.4.1 | Parquet read/write |
| Apache Arrow | 19.0.0 | Vortex in-memory columnar |
| Vortex JNI | 0.72.0 | Vortex file format |
| zstd-jni | 1.5.6-3 | ZSTD compression |
| JUnit 5 | 5.11.4 | Tests |
| Java | 25 | Runtime |
