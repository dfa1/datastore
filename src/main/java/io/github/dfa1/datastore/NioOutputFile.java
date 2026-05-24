package io.github.dfa1.datastore;

import org.apache.parquet.io.OutputFile;
import org.apache.parquet.io.PositionOutputStream;
import org.apache.parquet.io.DelegatingPositionOutputStream;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

class NioOutputFile implements OutputFile {

    private final Path path;

    NioOutputFile(Path path) {
        this.path = path;
    }

    @Override
    public PositionOutputStream create(long blockSizeHint) throws IOException {
        return createOrOverwrite(blockSizeHint);
    }

    @Override
    public PositionOutputStream createOrOverwrite(long blockSizeHint) throws IOException {
        var channel = Files.newByteChannel(path,
                StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return new DelegatingPositionOutputStream(Channels.newOutputStream(channel)) {
            @Override
            public long getPos() throws IOException {
                return channel.position();
            }
        };
    }

    @Override
    public boolean supportsBlockSize() {
        return false;
    }

    @Override
    public long defaultBlockSize() {
        return 0;
    }
}
