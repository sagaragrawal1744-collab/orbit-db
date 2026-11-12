
package com.orbitdb.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RecordStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPersistAndRetrieveRecordAfterReopeningDatabase()
            throws IOException {
        Path path = tempDir.resolve("records.db");

        DbRecord original = new DbRecord(1, Map.of(
                "name", "Sagar",
                "age", 20,
                "active", true
        ));

        try (DatabaseFile database = DatabaseFile.create(path)) {
            RecordStore store = new RecordStore(database);
            store.insert(original);

            assertEquals(1, database.getAllocatedPageCount());
        }

        try (DatabaseFile database = DatabaseFile.open(path)) {
            RecordStore store = new RecordStore(database);

            Optional<DbRecord> result = store.findById(1);

            assertTrue(result.isPresent());
            assertEquals("Sagar", result.get().getField("name"));
            assertEquals(20, result.get().getField("age"));
            assertEquals(true, result.get().getField("active"));
        }
    }

    @Test
    void shouldAllocateNewPageWhenExistingPageIsFull()
            throws IOException {
        Path path = tempDir.resolve("multiple-pages.db");
        String largeValue = "x".repeat(3500);

        try (DatabaseFile database = DatabaseFile.create(path)) {
            RecordStore store = new RecordStore(database);

            store.insert(new DbRecord(1, Map.of("data", largeValue)));
            store.insert(new DbRecord(2, Map.of("data", largeValue)));

            assertEquals(2, database.getAllocatedPageCount());

            assertTrue(store.findById(1).isPresent());
            assertTrue(store.findById(2).isPresent());
        }
    }

    @Test
    void shouldRejectDuplicateRecordIdsAcrossPages()
            throws IOException {
        Path path = tempDir.resolve("duplicates.db");
        String largeValue = "x".repeat(3500);

        try (DatabaseFile database = DatabaseFile.create(path)) {
            RecordStore store = new RecordStore(database);

            store.insert(new DbRecord(1, Map.of("data", largeValue)));
            store.insert(new DbRecord(2, Map.of("data", largeValue)));

            assertThrows(
                    IllegalArgumentException.class,
                    () -> store.insert(
                            new DbRecord(1, Map.of("name", "Duplicate"))
                    )
            );

            assertEquals(2, database.getAllocatedPageCount());
        }
    }

    @Test
    void shouldReturnEmptyWhenRecordDoesNotExist()
            throws IOException {
        Path path = tempDir.resolve("missing.db");

        try (DatabaseFile database = DatabaseFile.create(path)) {
            RecordStore store = new RecordStore(database);

            assertEquals(Optional.empty(), store.findById(999));
        }
    }

    @Test
    void shouldPersistRecordsAcrossMultiplePagesAfterReopening()
            throws IOException {
        Path path = tempDir.resolve("reopen-multiple.db");
        String largeValue = "x".repeat(3500);

        try (DatabaseFile database = DatabaseFile.create(path)) {
            RecordStore store = new RecordStore(database);

            store.insert(new DbRecord(10, Map.of("data", largeValue)));
            store.insert(new DbRecord(20, Map.of("data", largeValue)));
        }

        try (DatabaseFile database = DatabaseFile.open(path)) {
            RecordStore store = new RecordStore(database);

            assertEquals(2, database.getAllocatedPageCount());
            assertTrue(store.findById(10).isPresent());
            assertTrue(store.findById(20).isPresent());
        }
    }
}
