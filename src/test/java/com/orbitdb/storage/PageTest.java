package com.orbitdb.storage;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class PageTest {

    @Test
    void shouldPreservePageTypeAfterSerialization() {
        Page original = Page.create(
                7, PageType.DATA, new byte[]{1, 2, 3}
        );

        Page restored = Page.fromBytes(original.toBytes(), 7);

        assertEquals(PageType.DATA, restored.getType());
        assertEquals(7, restored.getPageId());
        assertArrayEquals(
                new byte[]{1, 2, 3},
                restored.getPayload()
        );
    }

    @Test
    void shouldDefaultToRawPageType() {
        Page page = Page.create(1, new byte[]{5});

        assertEquals(PageType.RAW, page.getType());
    }

    @Test
    void shouldRejectUnknownPageType() {
        byte[] bytes = Page.create(1, new byte[0]).toBytes();

        ByteBuffer.wrap(bytes)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(4, (1 << 24) | (99 << 16));

        assertThrows(
                IllegalArgumentException.class,
                () -> Page.fromBytes(bytes, 1)
        );
    }

    @Test
    void shouldRejectWrongPageId() {
        Page page = Page.create(1, new byte[0]);

        assertThrows(
                IllegalArgumentException.class,
                () -> Page.fromBytes(page.toBytes(), 2)
        );
    }
}