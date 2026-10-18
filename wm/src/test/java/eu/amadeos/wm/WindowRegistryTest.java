package eu.amadeos.wm;

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

    @Test
    void updatesStackingOrderOnBringToFrontAndSendToBack() {
        WindowRegistry registry = new WindowRegistry();
        registry.add(10, new WindowGeometry(0, 0, 100, 100));
        registry.add(20, new WindowGeometry(1, 1, 200, 200));
        registry.add(30, new WindowGeometry(2, 2, 300, 300));

        // Initial order: [10, 20, 30]
        assertEquals(java.util.List.of(10L, 20L, 30L), registry.snapshot().stream().map(WindowRecord::id).toList());

        // Bring 10 to front (top of stack) -> [20, 30, 10]
        registry.bringToFront(10);
        assertEquals(java.util.List.of(20L, 30L, 10L), registry.snapshot().stream().map(WindowRecord::id).toList());

        // Send 10 to back (bottom of stack) -> [10, 20, 30]
        registry.sendToBack(10);
        assertEquals(java.util.List.of(10L, 20L, 30L), registry.snapshot().stream().map(WindowRecord::id).toList());

        // Send 30 to back -> [30, 10, 20]
        registry.sendToBack(30);
        assertEquals(java.util.List.of(30L, 10L, 20L), registry.snapshot().stream().map(WindowRecord::id).toList());
    }
}

