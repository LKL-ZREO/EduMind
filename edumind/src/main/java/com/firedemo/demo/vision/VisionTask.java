package com.firedemo.demo.vision;

import java.util.Locale;

public enum VisionTask {
    DESCRIBE,
    OCR,
    TABLE,
    FORMULA,
    CODE,
    HOMEWORK;

    public static VisionTask from(String value) {
        if (value == null || value.isBlank()) return DESCRIBE;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return DESCRIBE;
        }
    }
}
