
package com.orbitdb.storage;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public final class DbRecord {

    private static final int MAX_FIELDS = 128;
    private static final int MAX_FIELD_NAME_BYTES = 255;

    private final long id;
    private final Map<String, Object> fields;

    public DbRecord(long id, Map<String, ?> fields) {
        if (id <= 0)
            throw new IllegalArgumentException("Record ID must be positive");

        if (fields == null || fields.size() > MAX_FIELDS)
            throw new IllegalArgumentException("Invalid field count");

        Map<String, Object> sorted = new TreeMap<>();

        for (Map.Entry<String, ?> entry : fields.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();

            if (name == null || name.isBlank())
                throw new IllegalArgumentException("Invalid field name");

            byte[] nameBytes = encodeUtf8(name);

            if (nameBytes.length > MAX_FIELD_NAME_BYTES)
                throw new IllegalArgumentException("Field name is too long");

            if (!(value == null || value instanceof String ||
                    value instanceof Integer || value instanceof Long ||
                    value instanceof Double || value instanceof Boolean))
                throw new IllegalArgumentException("Unsupported field type: " +
                        value.getClass().getName());

            if (value instanceof Double number && !Double.isFinite(number))
                throw new IllegalArgumentException(
                        "Non-finite double values are not supported"
                );

            sorted.put(name, value);
        }

        this.id = id;
        this.fields = Collections.unmodifiableMap(sorted);
    }

    private static byte[] encodeUtf8(String value) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(java.nio.CharBuffer.wrap(value));

            byte[] result = new byte[encoded.remaining()];
            encoded.get(result);
            return result;
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Invalid Unicode string", e);
        }
    }

    public long getId() {
        return id;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public Object getField(String name) {
        return fields.get(name);
    }
}
