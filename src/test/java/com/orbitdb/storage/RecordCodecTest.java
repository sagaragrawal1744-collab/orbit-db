
package com.orbitdb.storage;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecordCodecTest {

    @Test
    void shouldEncodeAndDecodeAllSupportedTypes() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("name", "Sagar");
        fields.put("age", 20);
        fields.put("balance", 25000.50);
        fields.put("largeNumber", 9000000000L);
        fields.put("active", true);
        fields.put("deleted", null);

        DbRecord original = new DbRecord(101, fields);
        DbRecord decoded = RecordCodec.decode(RecordCodec.encode(original));

        assertEquals(original.getId(), decoded.getId());
        assertEquals(original.getFields(), decoded.getFields());
    }

    @Test
    void shouldProduceDeterministicEncoding() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("name", "Sagar");
        first.put("age", 20);

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("age", 20);
        second.put("name", "Sagar");

        assertArrayEquals(
                RecordCodec.encode(new DbRecord(1, first)),
                RecordCodec.encode(new DbRecord(1, second))
        );
    }

    @Test
    void shouldHandleEmptyRecords() {
        DbRecord original = new DbRecord(1, Map.of());
        DbRecord decoded = RecordCodec.decode(RecordCodec.encode(original));

        assertEquals(original.getId(), decoded.getId());
        assertTrue(decoded.getFields().isEmpty());
    }

    @Test
    void shouldRejectInvalidRecordIds() {
        assertThrows(IllegalArgumentException.class,
                () -> new DbRecord(0, Map.of()));

        assertThrows(IllegalArgumentException.class,
                () -> new DbRecord(-1, Map.of()));
    }

    @Test
    void shouldRejectUnsupportedFieldTypes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DbRecord(1, Map.of("items", new int[]{1, 2, 3}))
        );
    }

    @Test
    void shouldRejectNonFiniteDoubles() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DbRecord(1, Map.of("value", Double.NaN))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new DbRecord(1, Map.of("value", Double.POSITIVE_INFINITY))
        );
    }

    @Test
    void shouldRejectOversizedRecords() {
        DbRecord record = new DbRecord(
                1, Map.of("data", "x".repeat(5000))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> RecordCodec.encode(record)
        );
    }

    @Test
    void shouldDetectChecksumCorruption() {
        byte[] encoded = RecordCodec.encode(
                new DbRecord(1, Map.of("name", "Sagar"))
        );

        encoded[encoded.length - 1] ^= 1;

        assertThrows(
                IllegalArgumentException.class,
                () -> RecordCodec.decode(encoded)
        );
    }

    @Test
    void shouldRejectTruncatedRecords() {
        byte[] encoded = RecordCodec.encode(
                new DbRecord(1, Map.of("name", "Sagar"))
        );

        byte[] truncated = new byte[encoded.length - 1];
        System.arraycopy(encoded, 0, truncated, 0, truncated.length);

        assertThrows(
                IllegalArgumentException.class,
                () -> RecordCodec.decode(truncated)
        );
    }

    @Test
    void shouldExposeImmutableFields() {
        DbRecord record = new DbRecord(
                1, Map.of("name", "Sagar")
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> record.getFields().put("age", 20)
        );
    }

    @Test
    void shouldPreserveNullValues() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("deletedAt", null);

        DbRecord decoded = RecordCodec.decode(
                RecordCodec.encode(new DbRecord(1, fields))
        );

        assertTrue(decoded.getFields().containsKey("deletedAt"));
        assertNull(decoded.getField("deletedAt"));
    }

    @Test
    void shouldRejectMalformedUnicodeFieldNames() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DbRecord(1, Map.of("\uD800", "value"))
        );
    }

    @Test
    void shouldRejectMalformedUnicodeStringValues() {
        DbRecord record = new DbRecord(
                1, Map.of("value", "\uD800")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> RecordCodec.encode(record)
        );
    }
}
