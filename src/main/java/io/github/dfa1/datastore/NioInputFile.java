package io.github.dfa1.datastore;

import org.apache.parquet.io.DelegatingSeekableInputStream;
import org.apache.parquet.io.InputFile;
import org.apache.parquet.io.SeekableInputStream;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

class NioInputFile implements InputFile {

    private final Path path;

    NioInputFile(Path path) {
        this.path = path;
    }

    @Override
    public long getLength() throws IOException {
        return Files.size(path);
    }

    @Override
    public SeekableInputStream newStream() throws IOException {
        SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ);
        return new DelegatingSeekableInputStream(Channels.newInputStream(channel)) {
            @Override
            public long getPos() throws IOException {
                return channel.position();
            }

            @Override
            public void seek(long newPos) throws IOException {
                channel.position(newPos);
            }
        };
    }
}
