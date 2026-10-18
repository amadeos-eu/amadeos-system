package eu.amadeos.wm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WindowManagerCoreTest {
    @Test
    void delegatesLifecycleAndPlacementToAdapter() {
        RecordingAdapter adapter = new RecordingAdapter();
        WindowManagerCore manager = new WindowManagerCore(
                new WindowRegistry(), adapter, new FloatingLayoutPolicy(0, 0));

        manager.manage(7, new WindowGeometry(0, 0, 50, 40));
        manager.arrange(100, 100);
        manager.focus(7);
        manager.raise(7);
        manager.close(7);
        manager.unmanage(7);

        assertEquals(List.of("map:7", "configure:7:50x40", "focus:7", "raise:7", "close:7", "unmap:7"), adapter.events);
    }

    private static final class RecordingAdapter implements X11Adapter {
        private final List<String> events = new ArrayList<>();

        public void configureWindow(long id, WindowGeometry geometry) {
            events.add("configure:" + id + ":" + geometry.width() + "x" + geometry.height());
        }

        public void mapWindow(long id) { events.add("map:" + id); }
        public void unmapWindow(long id) { events.add("unmap:" + id); }
        public void focusWindow(long id) { events.add("focus:" + id); }
        public void raiseWindow(long id) { events.add("raise:" + id); }
        public void lowerWindow(long id) { events.add("lower:" + id); }
        public void closeWindow(long id) { events.add("close:" + id); }
    }
}

