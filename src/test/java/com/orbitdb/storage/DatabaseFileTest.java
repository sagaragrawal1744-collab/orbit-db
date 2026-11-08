
package com.orbitdb.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.NoSuchFileException;

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
                IllegalArgumentException.class,
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
}
