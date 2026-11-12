
package com.orbitdb.storage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32C;

public final class RecordCodec {

    private static final int MAGIC = 0x4F524543;
    private static final short VERSION = 1;
    private static final int HEADER_SIZE = 20;
    private static final int CHECKSUM_SIZE = 4;
    private static final int MAX_RECORD_SIZE = Page.PAGE_SIZE - 16;
    private static final int MAX_FIELDS = 128;
    private static final int MAX_FIELD_NAME_BYTES = 255;

    private static final byte NULL = 0;
    private static final byte STRING = 1;
    private static final byte INTEGER = 2;
    private static final byte LONG = 3;
    private static final byte DOUBLE = 4;
    private static final byte BOOLEAN = 5;

    private RecordCodec() {
    }

    public static byte[] encode(DbRecord record) {
        if (record == null)
            throw new IllegalArgumentException("Record cannot be null");

        Map<String, Object> fields = record.getFields();

        if (fields.size() > MAX_FIELDS)
            throw new IllegalArgumentException("Too many fields");

        int size = HEADER_SIZE + CHECKSUM_SIZE;
        Map<String, byte[]> names = new LinkedHashMap<>();
        Map<String, byte[]> values = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            byte[] name = encodeUtf8(entry.getKey());

            if (name.length == 0 || name.length > MAX_FIELD_NAME_BYTES)
                throw new IllegalArgumentException("Invalid field name length");

            byte[] value = encodeValue(entry.getValue());

            size = Math.addExact(size, 2 + name.length + 1 + 4 + value.length);

            if (size > MAX_RECORD_SIZE)
                throw new IllegalArgumentException("Record exceeds maximum size");

            names.put(entry.getKey(), name);
            values.put(entry.getKey(), value);
        }

        ByteBuffer buffer = ByteBuffer.allocate(size)
                .order(ByteOrder.BIG_ENDIAN);

        buffer.putInt(MAGIC);
        buffer.putShort(VERSION);
        buffer.putShort((short) 0);
        buffer.putLong(record.getId());
        buffer.putInt(fields.size());

        for (String name : fields.keySet()) {
            byte[] nameBytes = names.get(name);
            byte[] valueBytes = values.get(name);

            buffer.putShort((short) nameBytes.length);
            buffer.put(nameBytes);
            buffer.put(typeOf(fields.get(name)));
            buffer.putInt(valueBytes.length);
            buffer.put(valueBytes);
        }

        CRC32C checksum = new CRC32C();
        checksum.update(buffer.array(), 0, size - CHECKSUM_SIZE);
        buffer.putInt((int) checksum.getValue());

        return buffer.array();
    }

    public static DbRecord decode(byte[] data) {
        if (data == null || data.length < HEADER_SIZE + CHECKSUM_SIZE ||
                data.length > MAX_RECORD_SIZE)
            throw new IllegalArgumentException("Invalid record size");

        CRC32C checksum = new CRC32C();
        checksum.update(data, 0, data.length - CHECKSUM_SIZE);

        ByteBuffer buffer = ByteBuffer.wrap(data)
                .order(ByteOrder.BIG_ENDIAN);

        int storedChecksum = buffer.getInt(data.length - CHECKSUM_SIZE);

        if (storedChecksum != (int) checksum.getValue())
            throw new IllegalArgumentException("Record checksum mismatch");

        int magic = buffer.getInt();
        short version = buffer.getShort();
        short flags = buffer.getShort();
        long id = buffer.getLong();
        int fieldCount = buffer.getInt();

        if (magic != MAGIC || version != VERSION || flags != 0 ||
                id <= 0 || fieldCount < 0 || fieldCount > MAX_FIELDS)
            throw new IllegalArgumentException("Invalid record header");

        Map<String, Object> fields = new LinkedHashMap<>();

        for (int i = 0; i < fieldCount; i++) {
            requireRemaining(buffer, 2);

            int nameLength = Short.toUnsignedInt(buffer.getShort());

            if (nameLength == 0 || nameLength > MAX_FIELD_NAME_BYTES)
                throw new IllegalArgumentException("Invalid field name length");

            requireRemaining(buffer, nameLength + 5);

            String name = decodeUtf8(readBytes(buffer, nameLength));

            if (name.isBlank() || fields.containsKey(name))
                throw new IllegalArgumentException("Invalid or duplicate field name");

            byte type = buffer.get();
            int valueLength = buffer.getInt();

            if (valueLength < 0 ||
                    valueLength > buffer.remaining() - CHECKSUM_SIZE)
                throw new IllegalArgumentException("Invalid field value length");

            byte[] value = readBytes(buffer, valueLength);
            fields.put(name, decodeValue(type, value));
        }

        if (buffer.remaining() != CHECKSUM_SIZE)
            throw new IllegalArgumentException("Unexpected record data");

        buffer.getInt();

        try {
            return new DbRecord(id, fields);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid record data", e);
        }
    }

    private static byte[] encodeValue(Object value) {
        if (value == null)
            return new byte[0];

        if (value instanceof String string) {
            if (string.length() > MAX_RECORD_SIZE)
                throw new IllegalArgumentException("String exceeds maximum record size");

            return encodeUtf8(string);
        }

        if (value instanceof Integer number)
            return ByteBuffer.allocate(4).putInt(number).array();

        if (value instanceof Long number)
            return ByteBuffer.allocate(8).putLong(number).array();

        if (value instanceof Double number)
            return ByteBuffer.allocate(8).putDouble(number).array();

        if (value instanceof Boolean bool)
            return new byte[]{(byte) (bool ? 1 : 0)};

        throw new IllegalArgumentException("Unsupported value type");
    }

    private static byte[] encodeUtf8(String value) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));

            byte[] result = new byte[encoded.remaining()];
            encoded.get(result);
            return result;
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Invalid Unicode string", e);
        }
    }

    private static byte typeOf(Object value) {
        if (value == null) return NULL;
        if (value instanceof String) return STRING;
        if (value instanceof Integer) return INTEGER;
        if (value instanceof Long) return LONG;
        if (value instanceof Double) return DOUBLE;
        if (value instanceof Boolean) return BOOLEAN;

        throw new IllegalArgumentException("Unsupported value type");
    }

    private static Object decodeValue(byte type, byte[] value) {
        ByteBuffer buffer = ByteBuffer.wrap(value)
                .order(ByteOrder.BIG_ENDIAN);

        return switch (type) {
            case NULL -> {
                requireLength(value, 0);
                yield null;
            }
            case STRING -> decodeUtf8(value);
            case INTEGER -> {
                requireLength(value, 4);
                yield buffer.getInt();
            }
            case LONG -> {
                requireLength(value, 8);
                yield buffer.getLong();
            }
            case DOUBLE -> {
                requireLength(value, 8);
                double number = buffer.getDouble();

                if (!Double.isFinite(number))
                    throw new IllegalArgumentException("Invalid double value");

                yield number;
            }
            case BOOLEAN -> {
                requireLength(value, 1);

                if (value[0] != 0 && value[0] != 1)
                    throw new IllegalArgumentException("Invalid boolean value");

                yield value[0] == 1;
            }
            default -> throw new IllegalArgumentException("Unknown field type");
        };
    }

    private static String decodeUtf8(byte[] data) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Invalid UTF-8 data", e);
        }
    }

    private static byte[] readBytes(ByteBuffer buffer, int length) {
        byte[] result = new byte[length];
        buffer.get(result);
        return result;
    }

    private static void requireRemaining(ByteBuffer buffer, int length) {
        if (length < 0 || buffer.remaining() < length + CHECKSUM_SIZE)
            throw new IllegalArgumentException("Truncated record");
    }

    private static void requireLength(byte[] value, int expected) {
        if (value.length != expected)
            throw new IllegalArgumentException("Invalid field value size");
    }
}
