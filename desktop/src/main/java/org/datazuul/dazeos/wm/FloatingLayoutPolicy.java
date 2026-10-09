package org.datazuul.dazeos.wm;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FloatingLayoutPolicy {
    private final int margin;
    private final int offset;

    public FloatingLayoutPolicy(int margin, int offset) {
        if (margin < 0 || offset < 0) {
            throw new IllegalArgumentException("margin and offset must not be negative");
        }
        this.margin = margin;
        this.offset = offset;
    }

    public Map<Long, WindowGeometry> arrange(Collection<WindowRecord> windows,
                                               int screenWidth, int screenHeight) {
        if (screenWidth <= 0 || screenHeight <= 0) {
            throw new IllegalArgumentException("Screen dimensions must be positive");
        }
        Map<Long, WindowGeometry> result = new LinkedHashMap<>();
        int index = 0;
        for (WindowRecord window : windows) {
            int x = margin + index * offset;
            int y = margin + index * offset;
            int width = Math.min(window.geometry().width(), Math.max(1, screenWidth - x - margin));
            int height = Math.min(window.geometry().height(), Math.max(1, screenHeight - y - margin));
            result.put(window.id(), new WindowGeometry(x, y, width, height));
            index++;
        }
        return Map.copyOf(result);
    }
}

