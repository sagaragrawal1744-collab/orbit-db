
package com.orbitdb.storage;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class PageStore implements AutoCloseable {

    private final FileChannel channel;

    public PageStore(Path path) throws IOException {
        channel = FileChannel.open(path,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE);
    }

    public void writePage(Page page) throws IOException {
        long position = (long) page.getPageId() * Page.PAGE_SIZE;
        ByteBuffer buffer = ByteBuffer.wrap(page.toBytes());

        while (buffer.hasRemaining()) {
            int written = channel.write(buffer, position);
            if (written == 0) continue;
            position += written;
        }

        channel.force(true);
    }

    public Page readPage(int pageId) throws IOException {
        if (pageId < 0)
            throw new IllegalArgumentException("Invalid page ID");

        long position = (long) pageId * Page.PAGE_SIZE;

        if (position + Page.PAGE_SIZE > channel.size())
            throw new EOFException("Page does not exist");

        ByteBuffer buffer = ByteBuffer.allocate(Page.PAGE_SIZE);

        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, position);
            if (read < 0)
                throw new EOFException("Incomplete page");

            if (read == 0) continue;
            position += read;
        }

        return Page.fromBytes(buffer.array(), pageId);
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
