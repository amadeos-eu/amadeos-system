package eu.amadeos.wm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class FloatingLayoutPolicyTest {
    @Test
    void cascadesWindowsAndClipsToScreen() {
        WindowRecord first = new WindowRecord(1, new WindowGeometry(0, 0, 90, 80));
        WindowRecord second = new WindowRecord(2, new WindowGeometry(0, 0, 90, 80));

        var result = new FloatingLayoutPolicy(10, 15).arrange(List.of(first, second), 100, 100);

        assertEquals(new WindowGeometry(10, 10, 80, 80), result.get(1L));
        assertEquals(new WindowGeometry(25, 25, 65, 65), result.get(2L));
    }
}

