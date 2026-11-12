
package com.orbitdb.storage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32C;

public final class SlottedPage {

    private static final int MAGIC = 0x4F525047;
    private static final short VERSION = 1;

    private static final int HEADER_SIZE = 28;
    private static final int SLOT_SIZE = 8;
    private static final int CHECKSUM_SIZE = 4;
    private static final int PAYLOAD_SIZE = Page.PAGE_SIZE - 16;
    private static final int CHECKSUM_OFFSET = PAYLOAD_SIZE - CHECKSUM_SIZE;

    private final byte[] data;
    private int slotCount;
    private int recordCount;
    private int freeStart;
    private int freeEnd;
    private final int pageId;

    private SlottedPage(int pageId) {
        if (pageId <= 0)
            throw new IllegalArgumentException("Page ID must be positive");

        this.pageId = pageId;
        this.data = new byte[PAYLOAD_SIZE];
        this.slotCount = 0;
        this.recordCount = 0;
        this.freeStart = HEADER_SIZE;
        this.freeEnd = CHECKSUM_OFFSET;

        writeHeader();
    }

    private SlottedPage(byte[] data, int pageId, int slotCount,
                        int recordCount, int freeStart, int freeEnd) {
        this.data = data;
        this.pageId = pageId;
        this.slotCount = slotCount;
        this.recordCount = recordCount;
        this.freeStart = freeStart;
        this.freeEnd = freeEnd;
    }

    public static SlottedPage create(int pageId) {
        return new SlottedPage(pageId);
    }

    public static SlottedPage fromPayload(byte[] payload, int expectedPageId) {
        if (payload == null || payload.length != PAYLOAD_SIZE)
            throw new IllegalArgumentException("Invalid page payload size");

        if (expectedPageId <= 0)
            throw new IllegalArgumentException("Invalid expected page ID");

        byte[] copy = payload.clone();
        ByteBuffer buffer = ByteBuffer.wrap(copy)
                .order(ByteOrder.BIG_ENDIAN);

        CRC32C checksum = new CRC32C();
        checksum.update(copy, 0, CHECKSUM_OFFSET);

        int storedChecksum = buffer.getInt(CHECKSUM_OFFSET);

        if (storedChecksum != (int) checksum.getValue())
            throw new IllegalArgumentException("Page checksum mismatch");

        int magic = buffer.getInt();
        short version = buffer.getShort();
        short flags = buffer.getShort();
        int pageId = buffer.getInt();
        int slotCount = buffer.getInt();
        int recordCount = buffer.getInt();
        int freeStart = buffer.getInt();
        int freeEnd = buffer.getInt();

        if (magic != MAGIC || version != VERSION || flags != 0 ||
                pageId != expectedPageId ||
                slotCount < 0 ||
                recordCount != slotCount ||
                slotCount > (CHECKSUM_OFFSET - HEADER_SIZE) / SLOT_SIZE ||
                freeStart != HEADER_SIZE + slotCount * SLOT_SIZE ||
                freeEnd < freeStart || freeEnd > CHECKSUM_OFFSET)
            throw new IllegalArgumentException("Invalid page header");

        SlottedPage page = new SlottedPage(
                copy, pageId, slotCount, recordCount, freeStart, freeEnd
        );

        page.validateRecords();
        return page;
    }

    public synchronized int insert(DbRecord record) {
        if (record == null)
            throw new IllegalArgumentException("Record cannot be null");

        byte[] encoded = RecordCodec.encode(record);
        int required = SLOT_SIZE + encoded.length;

        if (required > getFreeSpace())
            throw new IllegalStateException("Insufficient page space");

        for (int i = 0; i < slotCount; i++) {
            if (read(i).getId() == record.getId())
                throw new IllegalArgumentException("Duplicate record ID on page");
        }

        int newFreeEnd = freeEnd - encoded.length;
        int slotPosition = HEADER_SIZE + slotCount * SLOT_SIZE;

        System.arraycopy(encoded, 0, data, newFreeEnd, encoded.length);

        ByteBuffer slot = ByteBuffer.wrap(data)
                .order(ByteOrder.BIG_ENDIAN);

        slot.putInt(slotPosition, newFreeEnd);
        slot.putInt(slotPosition + 4, encoded.length);

        slotCount++;
        recordCount++;
        freeStart += SLOT_SIZE;
        freeEnd = newFreeEnd;

        writeHeader();
        return slotCount - 1;
    }

    public synchronized DbRecord read(int slotId) {
        if (slotId < 0 || slotId >= slotCount)
            throw new IllegalArgumentException("Invalid slot ID");

        ByteBuffer buffer = ByteBuffer.wrap(data)
                .order(ByteOrder.BIG_ENDIAN);

        int slotPosition = HEADER_SIZE + slotId * SLOT_SIZE;
        int offset = buffer.getInt(slotPosition);
        int length = buffer.getInt(slotPosition + 4);

        if (length <= 0 || offset < freeEnd ||
                offset > CHECKSUM_OFFSET - length)
            throw new IllegalStateException("Corrupt slot entry");

        byte[] encoded = new byte[length];
        System.arraycopy(data, offset, encoded, 0, length);

        return RecordCodec.decode(encoded);
    }

    public synchronized byte[] toPayload() {
        byte[] result = data.clone();
        ByteBuffer buffer = ByteBuffer.wrap(result)
                .order(ByteOrder.BIG_ENDIAN);

        CRC32C checksum = new CRC32C();
        checksum.update(result, 0, CHECKSUM_OFFSET);

        buffer.putInt(CHECKSUM_OFFSET, (int) checksum.getValue());
        return result;
    }

    public synchronized int getFreeSpace() {
        return freeEnd - freeStart;
    }

    public synchronized int getSlotCount() {
        return slotCount;
    }

    public synchronized int getRecordCount() {
        return recordCount;
    }

    public int getPageId() {
        return pageId;
    }

    private void writeHeader() {
        ByteBuffer buffer = ByteBuffer.wrap(data)
                .order(ByteOrder.BIG_ENDIAN);

        buffer.putInt(0, MAGIC);
        buffer.putShort(4, VERSION);
        buffer.putShort(6, (short) 0);
        buffer.putInt(8, pageId);
        buffer.putInt(12, slotCount);
        buffer.putInt(16, recordCount);
        buffer.putInt(20, freeStart);
        buffer.putInt(24, freeEnd);
    }

    private void validateRecords() {
        ByteBuffer buffer = ByteBuffer.wrap(data)
                .order(ByteOrder.BIG_ENDIAN);

        for (int i = freeStart; i < freeEnd; i++) {
            if (data[i] != 0)
                throw new IllegalArgumentException("Non-zero unused page space");
        }

        List<int[]> ranges = new ArrayList<>();
        Set<Long> recordIds = new HashSet<>();
        int totalLength = 0;

        for (int i = 0; i < slotCount; i++) {
            int slotPosition = HEADER_SIZE + i * SLOT_SIZE;
            int offset = buffer.getInt(slotPosition);
            int length = buffer.getInt(slotPosition + 4);

            if (length <= 0 || offset < freeEnd ||
                    offset > CHECKSUM_OFFSET - length)
                throw new IllegalArgumentException("Invalid slot entry");

            ranges.add(new int[]{offset, offset + length});
            totalLength = Math.addExact(totalLength, length);

            byte[] encoded = new byte[length];
            System.arraycopy(data, offset, encoded, 0, length);

            DbRecord record = RecordCodec.decode(encoded);

            if (!recordIds.add(record.getId()))
                throw new IllegalArgumentException("Duplicate record ID");
        }

        ranges.sort(Comparator.comparingInt(range -> range[0]));

        int expectedOffset = freeEnd;

        for (int[] range : ranges) {
            if (range[0] != expectedOffset)
                throw new IllegalArgumentException("Overlapping or missing record data");

            expectedOffset = range[1];
        }

        if (expectedOffset != CHECKSUM_OFFSET ||
                totalLength != CHECKSUM_OFFSET - freeEnd)
            throw new IllegalArgumentException("Invalid record data boundaries");
    }
}
