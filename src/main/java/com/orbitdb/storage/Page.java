
package com.orbitdb.storage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class Page {

    public static final int PAGE_SIZE = 4096;
    private static final int HEADER_SIZE = 16;
    private static final int MAGIC = 0x4F524254;
    private static final int VERSION = 1;

    private final byte[] data;

    private Page(byte[] data) {
        this.data = data;
    }

    public static Page create(int pageId, byte[] payload) {
        if (pageId < 0 || payload.length > PAGE_SIZE - HEADER_SIZE)
            throw new IllegalArgumentException("Invalid page data");

        ByteBuffer buffer = ByteBuffer.allocate(PAGE_SIZE)
                .order(ByteOrder.BIG_ENDIAN);

        buffer.putInt(MAGIC);
        buffer.putInt(VERSION);
        buffer.putInt(pageId);
        buffer.putInt(payload.length);
        buffer.put(payload);

        return new Page(buffer.array());
    }

    public static Page fromBytes(byte[] data, int expectedId) {
        if (data.length != PAGE_SIZE)
            throw new IllegalArgumentException("Invalid page size");

        ByteBuffer buffer = ByteBuffer.wrap(data)
                .order(ByteOrder.BIG_ENDIAN);

        int magic = buffer.getInt();
        int version = buffer.getInt();
        int pageId = buffer.getInt();
        int length = buffer.getInt();

        if (magic != MAGIC || version != VERSION ||
                pageId != expectedId || length < 0 ||
                length > PAGE_SIZE - HEADER_SIZE)
            throw new IllegalArgumentException("Corrupted page");

        return new Page(data.clone());
    }

    public int getPageId() {
        return ByteBuffer.wrap(data).getInt(8);
    }

    public byte[] getPayload() {
        int length = ByteBuffer.wrap(data).getInt(12);
        return Arrays.copyOfRange(data, HEADER_SIZE, HEADER_SIZE + length);
    }

    public byte[] toBytes() {
        return data.clone();
    }
}
