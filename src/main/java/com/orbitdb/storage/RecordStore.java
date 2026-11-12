
package com.orbitdb.storage;

import java.io.IOException;
import java.util.Optional;

public final class RecordStore {

    private static final int SLOT_SIZE = 8;

    private final DatabaseFile database;

    public RecordStore(DatabaseFile database) {
        if (database == null)
            throw new IllegalArgumentException("Database cannot be null");

        this.database = database;
    }

    public void insert(DbRecord record) throws IOException {
        if (record == null)
            throw new IllegalArgumentException("Record cannot be null");

        synchronized (database) {
            int requiredSpace = SLOT_SIZE + RecordCodec.encode(record).length;
            int allocatedPages = database.getAllocatedPageCount();

            SlottedPage targetPage = null;

            for (int pageId = 1; pageId <= allocatedPages; pageId++) {
                SlottedPage page = loadPage(pageId);

                for (int slot = 0; slot < page.getSlotCount(); slot++) {
                    if (page.read(slot).getId() == record.getId())
                        throw new IllegalArgumentException("Duplicate record ID");
                }

                if (targetPage == null && page.getFreeSpace() >= requiredSpace)
                    targetPage = page;
            }

            if (targetPage != null) {
                targetPage.insert(record);
                database.writePage(Page.create(
                        targetPage.getPageId(),
                        targetPage.toPayload()
                ));
                return;
            }

            int expectedPageId = allocatedPages + 1;
            SlottedPage newPage = SlottedPage.create(expectedPageId);
            newPage.insert(record);

            int allocatedPageId = database.allocatePage(newPage.toPayload());

            if (allocatedPageId != expectedPageId)
                throw new IOException("Unexpected allocated page ID");
        }
    }

    public Optional<DbRecord> findById(long recordId) throws IOException {
        if (recordId <= 0)
            throw new IllegalArgumentException("Record ID must be positive");

        synchronized (database) {
            int allocatedPages = database.getAllocatedPageCount();

            for (int pageId = 1; pageId <= allocatedPages; pageId++) {
                SlottedPage page = loadPage(pageId);

                for (int slot = 0; slot < page.getSlotCount(); slot++) {
                    DbRecord record = page.read(slot);

                    if (record.getId() == recordId)
                        return Optional.of(record);
                }
            }

            return Optional.empty();
        }
    }

    private SlottedPage loadPage(int pageId) throws IOException {
        try {
            Page storedPage = database.readPage(pageId);
            return SlottedPage.fromPayload(
                    storedPage.getPayload(),
                    pageId
            );
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new IOException("Corrupted slotted page: " + pageId, e);
        }
    }
}
