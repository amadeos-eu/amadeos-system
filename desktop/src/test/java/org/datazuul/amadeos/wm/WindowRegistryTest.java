package org.datazuul.amadeos.wm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class WindowRegistryTest {
    @Test
    void keepsInsertionOrderAndReturnsImmutableSnapshot() {
        WindowRegistry registry = new WindowRegistry();
        registry.add(10, new WindowGeometry(0, 0, 100, 100));
        registry.add(20, new WindowGeometry(1, 1, 200, 200));

        assertEquals(java.util.List.of(10L, 20L), registry.snapshot().stream().map(WindowRecord::id).toList());
        assertThrows(UnsupportedOperationException.class, () -> registry.snapshot().clear());
    }

    @Test
    void rejectsDuplicateAndUpdatesGeometry() {
        WindowRegistry registry = new WindowRegistry();
        registry.add(10, new WindowGeometry(0, 0, 100, 100));

        assertThrows(IllegalArgumentException.class,
                () -> registry.add(10, new WindowGeometry(0, 0, 100, 100)));
        registry.updateGeometry(10, new WindowGeometry(5, 6, 70, 80));
        assertEquals(new WindowGeometry(5, 6, 70, 80), registry.find(10).orElseThrow().geometry());
    }
}

