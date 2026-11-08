
package com.orbitdb.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PageStoreTest {

    @TempDir
    Path directory;

    @Test
    void shouldPersistAndRetrievePage() throws Exception {
        Path file = directory.resolve("test.db");
        byte[] original = "ORBIT DB is working".getBytes();

        try (PageStore store = new PageStore(file)) {
            store.writePage(Page.create(0, original));
        }

        try (PageStore store = new PageStore(file)) {
            Page page = store.readPage(0);

            assertEquals(0, page.getPageId());
            assertArrayEquals(original, page.getPayload());
        }
    }
}
