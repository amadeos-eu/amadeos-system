package org.datazuul.amadeos.wm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class WindowRegistry {
    private final Map<Long, WindowRecord> windows = new LinkedHashMap<>();

    public void add(long id, WindowGeometry initialGeometry) {
        if (windows.containsKey(id)) {
            throw new IllegalArgumentException("Window already managed: " + id);
        }
        windows.put(id, new WindowRecord(id, initialGeometry));
    }

    public boolean remove(long id) {
        return windows.remove(id) != null;
    }

    public Optional<WindowRecord> find(long id) {
        return Optional.ofNullable(windows.get(id));
    }

    public void updateGeometry(long id, WindowGeometry geometry) {
        if (!windows.containsKey(id)) {
            throw new IllegalArgumentException("Window is not managed: " + id);
        }
        windows.put(id, new WindowRecord(id, geometry));
    }

    public void bringToFront(long id) {
        WindowRecord rec = windows.remove(id);
        if (rec != null) {
            windows.put(id, rec);
        }
    }

    public void sendToBack(long id) {
        WindowRecord rec = windows.remove(id);
        if (rec != null) {
            Map<Long, WindowRecord> reordered = new LinkedHashMap<>();
            reordered.put(id, rec);
            reordered.putAll(windows);
            windows.clear();
            windows.putAll(reordered);
        }
    }

    public Collection<WindowRecord> snapshot() {
        return ListCopy.copyOf(windows.values());
    }

    private static final class ListCopy {
        private static <T> Collection<T> copyOf(Collection<T> values) {
            return java.util.List.copyOf(new ArrayList<>(values));
        }
    }
}

