
package com.orbitdb.storage;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.zip.CRC32C;

import static org.junit.jupiter.api.Assertions.*;

class SlottedPageTest {

    private static final int CHECKSUM_OFFSET = Page.PAGE_SIZE - 16 - 4;

    @Test
    void shouldCreateEmptyPage() {
        SlottedPage page = SlottedPage.create(1);

        assertEquals(1, page.getPageId());
        assertEquals(0, page.getRecordCount());
        assertEquals(0, page.getSlotCount());
        assertTrue(page.getFreeSpace() > 0);
    }

    @Test
    void shouldInsertAndReadRecord() {
        SlottedPage page = SlottedPage.create(1);
        DbRecord record = new DbRecord(101, Map.of("name", "Sagar"));

        int slot = page.insert(record);
        DbRecord result = page.read(slot);

        assertEquals(0, slot);
        assertEquals(record.getId(), result.getId());
        assertEquals(record.getFields(), result.getFields());
    }

    @Test
    void shouldStoreMultipleRecords() {
        SlottedPage page = SlottedPage.create(1);

        assertEquals(0, page.insert(
                new DbRecord(1, Map.of("name", "Sagar"))
        ));

        assertEquals(1, page.insert(
                new DbRecord(2, Map.of("name", "Rahul"))
        ));

        assertEquals(2, page.insert(
                new DbRecord(3, Map.of("name", "Aman"))
        ));

        assertEquals(3, page.getRecordCount());
        assertEquals(3, page.getSlotCount());

        assertEquals(1, page.read(0).getId());
        assertEquals(2, page.read(1).getId());
        assertEquals(3, page.read(2).getId());
    }

    @Test
    void shouldRejectDuplicateRecordIds() {
        SlottedPage page = SlottedPage.create(1);
        page.insert(new DbRecord(10, Map.of("name", "First")));

        assertThrows(
                IllegalArgumentException.class,
                () -> page.insert(new DbRecord(10, Map.of("name", "Second")))
        );

        assertEquals(1, page.getRecordCount());
    }

    @Test
    void shouldRejectInvalidSlotIds() {
        SlottedPage page = SlottedPage.create(1);

        assertThrows(IllegalArgumentException.class, () -> page.read(0));
        assertThrows(IllegalArgumentException.class, () -> page.read(-1));
    }

    @Test
    void shouldRejectRecordsWhenPageIsFull() {
        SlottedPage page = SlottedPage.create(1);
        String largeValue = "x".repeat(3500);

        page.insert(new DbRecord(1, Map.of("data", largeValue)));

        assertThrows(
                IllegalStateException.class,
                () -> page.insert(new DbRecord(2, Map.of("data", largeValue)))
        );

        assertEquals(1, page.getRecordCount());
    }

    @Test
    void failedInsertionShouldNotModifyPage() {
        SlottedPage page = SlottedPage.create(1);
        String largeValue = "x".repeat(3500);

        page.insert(new DbRecord(1, Map.of("data", largeValue)));
        byte[] before = page.toPayload();

        assertThrows(
                IllegalStateException.class,
                () -> page.insert(new DbRecord(2, Map.of("data", largeValue)))
        );

        assertArrayEquals(before, page.toPayload());
    }

    @Test
    void shouldPreserveRecordsAfterSerialization() {
        SlottedPage original = SlottedPage.create(5);

        original.insert(new DbRecord(1, Map.of("name", "Sagar")));
        original.insert(new DbRecord(2, Map.of("age", 20)));

        SlottedPage restored = SlottedPage.fromPayload(
                original.toPayload(), 5
        );

        assertEquals(2, restored.getRecordCount());
        assertEquals("Sagar", restored.read(0).getField("name"));
        assertEquals(20, restored.read(1).getField("age"));
        assertEquals(original.getFreeSpace(), restored.getFreeSpace());
        assertArrayEquals(original.toPayload(), restored.toPayload());
    }

    @Test
    void shouldRejectWrongPageId() {
        SlottedPage page = SlottedPage.create(5);

        assertThrows(
                IllegalArgumentException.class,
                () -> SlottedPage.fromPayload(page.toPayload(), 6)
        );
    }

    @Test
    void shouldDetectPageChecksumCorruption() {
        SlottedPage page = SlottedPage.create(1);
        page.insert(new DbRecord(1, Map.of("name", "Sagar")));

        byte[] payload = page.toPayload();
        payload[40] ^= 1;

        assertThrows(
                IllegalArgumentException.class,
                () -> SlottedPage.fromPayload(payload, 1)
        );
    }

    @Test
    void shouldRejectInvalidPageSize() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SlottedPage.fromPayload(new byte[100], 1)
        );
    }

    @Test
    void shouldRejectNonZeroUnusedPageSpace() {
        byte[] payload = SlottedPage.create(1).toPayload();
        payload[30] = 1;
        updateChecksum(payload);

        assertThrows(
                IllegalArgumentException.class,
                () -> SlottedPage.fromPayload(payload, 1)
        );
    }

    @Test
    void shouldRejectInvalidSlotOffset() {
        SlottedPage page = SlottedPage.create(1);
        page.insert(new DbRecord(1, Map.of("name", "Sagar")));

        byte[] payload = page.toPayload();
        ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN).putInt(28, 1);
        updateChecksum(payload);

        assertThrows(
                IllegalArgumentException.class,
                () -> SlottedPage.fromPayload(payload, 1)
        );
    }

    private static void updateChecksum(byte[] payload) {
        CRC32C checksum = new CRC32C();
        checksum.update(payload, 0, CHECKSUM_OFFSET);

        ByteBuffer.wrap(payload)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(CHECKSUM_OFFSET, (int) checksum.getValue());
    }
}
