package org.datazuul.amadeos.wm;

public record WindowRecord(long id, WindowGeometry geometry) {
    public WindowRecord {
        if (id <= 0) {
            throw new IllegalArgumentException("Window id must be positive");
        }
        if (geometry == null) {
            throw new NullPointerException("geometry");
        }
    }
}

