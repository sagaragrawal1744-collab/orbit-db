package com.orbitdb.storage;

public enum PageType {
    RAW(0),
    METADATA(1),
    DATA(2),
    INDEX(3);

    private final int code;

    PageType(int code) {
        this.code = code;
    }

    int getCode() {
        return code;
    }

    static PageType fromCode(int code) {
        for (PageType type : values()) {
            if (type.code == code)
                return type;
        }

        throw new IllegalArgumentException("Unknown page type: " + code);
    }
}