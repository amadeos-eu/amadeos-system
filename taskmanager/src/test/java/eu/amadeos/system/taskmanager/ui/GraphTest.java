package eu.amadeos.system.taskmanager.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraphTest {
    @Test
    void theScaleGrowsInPowersOfFour() {
        assertEquals(64 * 1024, Graph.niceTop(0, 64 * 1024));
        assertEquals(64 * 1024, Graph.niceTop(60_000, 64 * 1024));
        assertEquals(1024 * 1024, Graph.niceTop(300_000, 64 * 1024));
        assertEquals(4 * 1024 * 1024, Graph.niceTop(2_500_000, 1024 * 1024));
    }
}
