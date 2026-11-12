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
    private final PageType type;

    private Page(byte[] data, PageType type) {
        this.data = data;
        this.type = type;
    }

    public static Page create(int pageId, byte[] payload) {
        return create(pageId, PageType.RAW, payload);
    }

    public static Page create(int pageId, PageType type, byte[] payload) {
        if (pageId < 0 || type == null || payload == null ||
                payload.length > PAGE_SIZE - HEADER_SIZE)
            throw new IllegalArgumentException("Invalid page data");

        ByteBuffer buffer = ByteBuffer.allocate(PAGE_SIZE)
                .order(ByteOrder.BIG_ENDIAN);

        int versionAndType = (VERSION << 24) | (type.getCode() << 16);

        buffer.putInt(MAGIC);
        buffer.putInt(versionAndType);
        buffer.putInt(pageId);
        buffer.putInt(payload.length);
        buffer.put(payload);

        return new Page(buffer.array(), type);
    }

    public static Page fromBytes(byte[] data, int expectedId) {
        if (data == null || data.length != PAGE_SIZE)
            throw new IllegalArgumentException("Invalid page size");

        ByteBuffer buffer = ByteBuffer.wrap(data)
                .order(ByteOrder.BIG_ENDIAN);

        int magic = buffer.getInt();
        int versionAndType = buffer.getInt();
        int pageId = buffer.getInt();
        int length = buffer.getInt();

        int version = versionAndType >>> 24;
        int typeCode = (versionAndType >>> 16) & 0xFF;
        int reserved = versionAndType & 0xFFFF;

        if (magic != MAGIC || version != VERSION || reserved != 0 ||
                pageId != expectedId || length < 0 ||
                length > PAGE_SIZE - HEADER_SIZE)
            throw new IllegalArgumentException("Corrupted page");

        PageType type = PageType.fromCode(typeCode);
        return new Page(data.clone(), type);
    }

    public int getPageId() {
        return ByteBuffer.wrap(data)
                .order(ByteOrder.BIG_ENDIAN)
                .getInt(8);
    }

    public PageType getType() {
        return type;
    }

    public byte[] getPayload() {
        int length = ByteBuffer.wrap(data)
                .order(ByteOrder.BIG_ENDIAN)
                .getInt(12);

        return Arrays.copyOfRange(data, HEADER_SIZE, HEADER_SIZE + length);
    }

    public byte[] toBytes() {
        return data.clone();
    }
}