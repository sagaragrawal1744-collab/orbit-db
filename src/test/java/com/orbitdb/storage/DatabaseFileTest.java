
package com.orbitdb.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseFileTest {

    @TempDir
    Path directory;

    @Test
    void shouldCreateAndReopenDatabase() throws Exception {
        Path file = directory.resolve("orbit.db");
        long createdAt;

        try (DatabaseFile db = DatabaseFile.create(file)) {
            createdAt = db.getCreatedAt();
            assertEquals(file.toAbsolutePath(), db.getPath());
        }

        assertEquals(Page.PAGE_SIZE, Files.size(file));

        try (DatabaseFile db = DatabaseFile.open(file)) {
            assertEquals(createdAt, db.getCreatedAt());
        }
    }

    @Test
    void shouldNotOverwriteExistingDatabase() throws Exception {
        Path file = directory.resolve("orbit.db");

        try (DatabaseFile ignored = DatabaseFile.create(file)) {
            assertThrows(
                    FileAlreadyExistsException.class,
                    () -> DatabaseFile.create(file)
            );
        }
    }

    @Test
    void shouldRejectInvalidDatabase() throws Exception {
        Path file = directory.resolve("invalid.db");
        Files.write(file, new byte[Page.PAGE_SIZE]);

        assertThrows(
                IOException.class,
                () -> DatabaseFile.open(file)
        );
    }

    @Test
    void shouldNotCreateMissingDatabaseWhenOpening() {
        Path file = directory.resolve("missing.db");

        assertThrows(
                NoSuchFileException.class,
                () -> DatabaseFile.open(file)
        );

        assertFalse(Files.exists(file));
    }

    @Test
    void shouldAllocateAndPersistPages() throws Exception {
        Path file = directory.resolve("allocation.db");

        try (DatabaseFile db = DatabaseFile.create(file)) {
            assertEquals(1, db.allocatePage("First".getBytes()));
            assertEquals(2, db.allocatePage("Second".getBytes()));
            assertEquals(2, db.getAllocatedPageCount());

            assertArrayEquals(
                    "First".getBytes(),
                    db.readPage(1).getPayload()
            );
        }

        try (DatabaseFile db = DatabaseFile.open(file)) {
            assertEquals(2, db.getAllocatedPageCount());
            assertEquals(3, db.allocatePage("Third".getBytes()));

            assertArrayEquals(
                    "Third".getBytes(),
                    db.readPage(3).getPayload()
            );
        }
    }

    @Test
    void shouldRejectUnallocatedPages() throws Exception {
        Path file = directory.resolve("unallocated.db");

        try (DatabaseFile db = DatabaseFile.create(file)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> db.readPage(1)
            );

            assertThrows(
                    IllegalArgumentException.class,
                    () -> db.writePage(Page.create(1, new byte[0]))
            );
        }
    }
}
