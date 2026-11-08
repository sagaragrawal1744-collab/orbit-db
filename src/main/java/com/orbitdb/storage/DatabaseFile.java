
package com.orbitdb.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public final class DatabaseFile implements AutoCloseable {

    private static final int MAGIC = 0x4F524244;
    private static final int FORMAT_VERSION = 1;
    private static final int METADATA_SIZE = 20;

    private final Path path;
    private final PageStore store;
    private final long createdAt;

    private DatabaseFile(Path path, PageStore store, long createdAt) {
        this.path = path;
        this.store = store;
        this.createdAt = createdAt;
    }

    public static DatabaseFile create(Path path) throws IOException {
        Path file = path.toAbsolutePath();
        Files.createFile(file);

        PageStore store = null;

        try {
            store = new PageStore(file);
            long createdAt = System.currentTimeMillis();

            ByteBuffer metadata = ByteBuffer.allocate(METADATA_SIZE)
                    .order(ByteOrder.BIG_ENDIAN);

            metadata.putInt(MAGIC);
            metadata.putInt(FORMAT_VERSION);
            metadata.putInt(Page.PAGE_SIZE);
            metadata.putLong(createdAt);

            store.writePage(Page.create(0, metadata.array()));

            return new DatabaseFile(file, store, createdAt);
        } catch (IOException | RuntimeException e) {
            if (store != null) {
                try {
                    store.close();
                } catch (IOException closeError) {
                    e.addSuppressed(closeError);
                }
            }

            try {
                Files.deleteIfExists(file);
            } catch (IOException deleteError) {
                e.addSuppressed(deleteError);
            }

            throw e;
        }
    }

    public static DatabaseFile open(Path path) throws IOException {
        Path file = path.toAbsolutePath();

        if (!Files.isRegularFile(file))
            throw new NoSuchFileException(file.toString());

        PageStore store = new PageStore(file);

        try {
            byte[] payload = store.readPage(0).getPayload();

            if (payload.length != METADATA_SIZE)
                throw new IOException("Invalid database metadata");

            ByteBuffer metadata = ByteBuffer.wrap(payload)
                    .order(ByteOrder.BIG_ENDIAN);

            int magic = metadata.getInt();
            int version = metadata.getInt();
            int pageSize = metadata.getInt();
            long createdAt = metadata.getLong();

            if (magic != MAGIC || version != FORMAT_VERSION ||
                    pageSize != Page.PAGE_SIZE || createdAt <= 0)
                throw new IOException("Invalid database file");

            return new DatabaseFile(file, store, createdAt);
        } catch (IOException | RuntimeException e) {
            try {
                store.close();
            } catch (IOException closeError) {
                e.addSuppressed(closeError);
            }

            throw e;
        }
    }

    public Path getPath() {
        return path;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    @Override
    public void close() throws IOException {
        store.close();
    }
}
