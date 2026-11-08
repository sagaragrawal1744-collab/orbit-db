
package com.orbitdb.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public final class DatabaseFile implements AutoCloseable {

    private static final int MAGIC = 0x4F524244;
    private static final int FORMAT_VERSION = 2;
    private static final int METADATA_SIZE = 24;

    private final Path path;
    private final PageStore store;
    private final long createdAt;
    private int nextPageId;

    private DatabaseFile(Path path, PageStore store,
                         long createdAt, int nextPageId) {
        this.path = path;
        this.store = store;
        this.createdAt = createdAt;
        this.nextPageId = nextPageId;
    }

    public static DatabaseFile create(Path path) throws IOException {
        Path file = path.toAbsolutePath();
        Files.createFile(file);

        PageStore store = null;

        try {
            store = new PageStore(file);
            DatabaseFile db = new DatabaseFile(
                    file, store, System.currentTimeMillis(), 1
            );

            db.saveMetadata();
            return db;
        } catch (IOException | RuntimeException e) {
            if (store != null) {
                try {
                    store.close();
                } catch (IOException ex) {
                    e.addSuppressed(ex);
                }
            }

            try {
                Files.deleteIfExists(file);
            } catch (IOException ex) {
                e.addSuppressed(ex);
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
            int nextPageId = metadata.getInt();

            if (magic != MAGIC || version != FORMAT_VERSION ||
                    pageSize != Page.PAGE_SIZE ||
                    createdAt <= 0 || nextPageId < 1)
                throw new IOException("Invalid database file");

            return new DatabaseFile(
                    file, store, createdAt, nextPageId
            );
        } catch (IOException | RuntimeException e) {
            try {
                store.close();
            } catch (IOException ex) {
                e.addSuppressed(ex);
            }

            if (e instanceof IllegalArgumentException)
                throw new IOException("Corrupted database file", e);

            throw e;
        }
    }

    public synchronized int allocatePage(byte[] payload)
            throws IOException {
        if (nextPageId == Integer.MAX_VALUE)
            throw new IOException("Database page limit reached");

        int pageId = nextPageId;
        store.writePage(Page.create(pageId, payload));

        nextPageId++;

        try {
            saveMetadata();
        } catch (IOException | RuntimeException e) {
            nextPageId--;
            throw e;
        }

        return pageId;
    }

    public Page readPage(int pageId) throws IOException {
        if (pageId <= 0 || pageId >= nextPageId)
            throw new IllegalArgumentException("Invalid page ID");

        return store.readPage(pageId);
    }

    public synchronized void writePage(Page page) throws IOException {
        int pageId = page.getPageId();

        if (pageId <= 0 || pageId >= nextPageId)
            throw new IllegalArgumentException("Page is not allocated");

        store.writePage(page);
    }

    private void saveMetadata() throws IOException {
        ByteBuffer metadata = ByteBuffer.allocate(METADATA_SIZE)
                .order(ByteOrder.BIG_ENDIAN);

        metadata.putInt(MAGIC);
        metadata.putInt(FORMAT_VERSION);
        metadata.putInt(Page.PAGE_SIZE);
        metadata.putLong(createdAt);
        metadata.putInt(nextPageId);

        store.writePage(Page.create(0, metadata.array()));
    }

    public Path getPath() {
        return path;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public synchronized int getAllocatedPageCount() {
        return nextPageId - 1;
    }

    @Override
    public void close() throws IOException {
        store.close();
    }
}
