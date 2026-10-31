package eu.amadeos.system.notify;

import eu.amadeos.system.base.xdg.XdgDesktopEntry;
import org.freedesktop.dbus.types.Variant;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationServerTest {
    private final List<Notification> received = new ArrayList<>();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC);

    private NotificationServer server() {
        NotificationCore core = new NotificationCore(() -> NotificationSettings.DEFAULTS, s -> { },
                new NotificationCore.Screen() {
                    @Override
                    public void popup(Notification n, long timeoutMs) {
                        received.add(n);
                    }

                    @Override
                    public void unpop(long id) {
                    }

                    @Override
                    public void historyChanged() {
                    }

                    @Override
                    public boolean fullscreen() {
                        return false;
                    }

                    @Override
                    public boolean locked() {
                        return false;
                    }

                    @Override
                    public void beep() {
                    }

                    @Override
                    public void launch(String desktopEntry) {
                    }
                }, new NotificationCore.Signals() {
                    @Override
                    public void closed(long id, int reason) {
                    }

                    @Override
                    public void actionInvoked(long id, String key) {
                    }

                    @Override
                    public void stateChanged(int unseen, boolean doNotDisturb) {
                    }
                }, clock, new NotificationHistory(), () -> { });
        XdgDesktopEntry firefox = new XdgDesktopEntry("firefox-esr.desktop", "Firefox ESR", null, null, "firefox-esr",
                "firefox-esr %u", false, null, null, false, false, null, null, null, null);
        return new NotificationServer(core, id -> id.equals("firefox-esr") ? Optional.of(firefox) : Optional.empty(),
                clock, () -> { }, "1.0");
    }

    @Test
    void readsTheHints() {
        long id = server().notify("", 0, "", "Download finished", "file.iso", List.of("default", "Open"),
                Map.of("desktop-entry", new Variant<>("firefox-esr.desktop"), "urgency", new Variant<>((byte) 2),
                        "category", new Variant<>("transfer.complete")), 5000, ":1.9");
        assertEquals(1, id);
        Notification n = received.get(0);
        assertEquals("Firefox ESR", n.appName(), "the desktop entry's name when app_name is empty");
        assertEquals("firefox-esr", n.programKey());
        assertEquals("firefox-esr", n.icon(), "the desktop entry's icon");
        assertEquals(Notification.Urgency.CRITICAL, n.urgency());
        assertEquals("transfer.complete", n.category());
        assertEquals(":1.9", n.sender());
        assertTrue(n.hasDefaultAction());
        assertTrue(n.buttons().isEmpty());
    }

    @Test
    void imagePathWinsOverTheIcon() {
        server().notify("Mail", 0, "mail", "S", "", List.of(),
                Map.of("image-path", new Variant<>("file:///tmp/photo.png")), -1, "");
        assertEquals("/tmp/photo.png", received.get(0).icon());
    }

    @Test
    void imageData() {
        byte[] pixels = new byte[2 * 2 * 4];
        pixels[0] = (byte) 255;
        pixels[3] = (byte) 255;
        BufferedImage image = NotificationImages.fromImageData(new Object[]{2, 2, 8, true, 8, 4, pixels});
        assertNotNull(image);
        assertEquals(0xFFFF0000, image.getRGB(0, 0));
        assertEquals(0, image.getRGB(1, 1) >>> 24, "transparent");
        assertNull(NotificationImages.fromImageData(new Object[]{2, 2, 8, true, 8, 4, new byte[3]}), "too short");
        assertNull(NotificationImages.fromImageData("nonsense"));
    }

    @Test
    void urgencyBytesAreUnsigned() {
        assertEquals(2, NotificationServer.number(new Variant<>((byte) 2), 1));
        assertEquals(1, NotificationServer.number(null, 1));
    }
}
