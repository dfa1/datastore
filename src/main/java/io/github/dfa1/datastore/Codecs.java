package io.github.dfa1.datastore;

import com.github.luben.zstd.Zstd;
import org.apache.parquet.bytes.BytesInput;
import org.apache.parquet.compression.CompressionCodecFactory;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

final class Codecs implements CompressionCodecFactory {

    private static final Codecs INSTANCE = new Codecs();

    static CompressionCodecFactory factory() { return INSTANCE; }

    static BytesInputCompressor compressor(CompressionCodecName codec) {
        return switch (codec) {
            case UNCOMPRESSED -> Uncompressed.INSTANCE;
            case GZIP         -> new GzipCodec();
            case ZSTD         -> new ZstdCodec();
            default -> throw new IllegalArgumentException("Unsupported codec: " + codec);
        };
    }

    @Override public BytesInputCompressor getCompressor(CompressionCodecName codec)   { return compressor(codec); }
    @Override public BytesInputDecompressor getDecompressor(CompressionCodecName codec) {
        return switch (codec) {
            case UNCOMPRESSED -> Uncompressed.INSTANCE;
            case GZIP         -> new GzipCodec();
            case ZSTD         -> new ZstdCodec();
            default -> throw new IllegalArgumentException("Unsupported codec: " + codec);
        };
    }
    @Override public void release() {}

    // --- UNCOMPRESSED ---

    private static final class Uncompressed implements BytesInputCompressor, BytesInputDecompressor {
        static final Uncompressed INSTANCE = new Uncompressed();

        @Override public BytesInput compress(BytesInput bytes)                         { return bytes; }
        @Override public CompressionCodecName getCodecName()                           { return CompressionCodecName.UNCOMPRESSED; }
        @Override public void release()                                                {}
        @Override public BytesInput decompress(BytesInput bytes, int decompressedSize) { return bytes; }

        @Override
        public void decompress(ByteBuffer input, int compressedSize, ByteBuffer output, int decompressedSize) {
            ByteBuffer src = input.duplicate();
            src.limit(src.position() + compressedSize);
            output.put(src);
            input.position(input.position() + compressedSize);
        }
    }

    // --- GZIP ---

    private static final class GzipCodec implements BytesInputCompressor, BytesInputDecompressor {
        @Override
        public BytesInput compress(BytesInput bytes) throws IOException {
            var out = new ByteArrayOutputStream();
            try (var gz = new GZIPOutputStream(out)) { gz.write(bytes.toByteArray()); }
            return BytesInput.from(out.toByteArray());
        }

        @Override public CompressionCodecName getCodecName() { return CompressionCodecName.GZIP; }
        @Override public void release() {}

        @Override
        public BytesInput decompress(BytesInput bytes, int decompressedSize) throws IOException {
            try (var gz = new GZIPInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                return BytesInput.from(gz.readNBytes(decompressedSize));
            }
        }

        @Override
        public void decompress(ByteBuffer input, int compressedSize, ByteBuffer output, int decompressedSize)
                throws IOException {
            byte[] compressed = new byte[compressedSize];
            input.get(compressed);
            try (var gz = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                output.put(gz.readNBytes(decompressedSize));
            }
        }
    }

    // --- ZSTD ---

    private static final class ZstdCodec implements BytesInputCompressor, BytesInputDecompressor {
        @Override
        public BytesInput compress(BytesInput bytes) throws IOException {
            return BytesInput.from(Zstd.compress(bytes.toByteArray()));
        }

        @Override public CompressionCodecName getCodecName() { return CompressionCodecName.ZSTD; }
        @Override public void release() {}

        @Override
        public BytesInput decompress(BytesInput bytes, int decompressedSize) throws IOException {
            return BytesInput.from(Zstd.decompress(bytes.toByteArray(), decompressedSize));
        }

        @Override
        public void decompress(ByteBuffer input, int compressedSize, ByteBuffer output, int decompressedSize)
                throws IOException {
            ByteBuffer src = input.duplicate();
            src.limit(src.position() + compressedSize);
            long r = Zstd.decompress(output, src);
            if (Zstd.isError(r)) throw new IOException("ZSTD: " + Zstd.getErrorName(r));
            input.position(input.position() + compressedSize);
        }
    }
}
