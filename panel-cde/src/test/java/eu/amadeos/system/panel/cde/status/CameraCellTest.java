package eu.amadeos.system.panel.cde.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import eu.amadeos.commons.i18n.Messages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraCellTest {
    private static final Messages EN = Messages.load(CameraCell.class, NetworkCell.DESKTOP_TEXTS, Locale.ENGLISH);
    private static final Messages DE = Messages.load(CameraCell.class, NetworkCell.DESKTOP_TEXTS, Locale.GERMAN);

    @TempDir
    Path proc;

    @Test
    void shownOnlyWhileACameraIsInUse() {
        AtomicInteger relayouts = new AtomicInteger();
        CameraCell cell = new CameraCell(Set::of, EN);
        cell.onAvailabilityChanged(relayouts::incrementAndGet);
        assertFalse(cell.available());
        assertNull(cell.component().getToolTipText());

        cell.show(Set.of("firefox", "amade-webcam"));
        assertTrue(cell.available());
        assertEquals("Camera in use by amade-webcam, firefox", cell.component().getToolTipText());
        cell.show(Set.of("amade-webcam"));
        assertEquals("Camera in use by amade-webcam", cell.component().getToolTipText());
        assertEquals(1, relayouts.get(), "another program alone does not lay out the slot again");

        cell.show(Set.of());
        assertFalse(cell.available());
        assertEquals(2, relayouts.get());
        assertEquals("Kamera wird benutzt von cheese", new CameraCell(Set::of, DE).describe(Set.of("cheese")));
    }

    @Test
    void findsTheProgramsWithACameraOpen() throws IOException {
        process("1", "systemd", "0 (systemd) S 0 1", null);
        process("300", "java", "300 (java) S 1 300", "/dev/null",
                "java", "-Dx=y", "-jar", "/usr/share/amade-webcam/amade-webcam-1.0.0.jar");
        process("301", "ffmpeg", "301 (ffmpeg) S 300 300", "/dev/video0");
        process("400", "firefox", "400 (firefox) S 1 400", "/dev/video2");
        process("500", "bash", "500 (bash) S 1 500", "/dev/video0");
        process("600", "code", "600 (my code) S 1 600", "/dev/pts/1");
        Files.createDirectories(proc.resolve("self"));

        assertEquals(Set.of("amade-webcam", "bash", "firefox"), new CameraUsers(proc).programs());
    }

    @Test
    void javaWithoutJarAndMissingProc() throws IOException {
        process("10", "java", "10 (java) S 1 10", "/dev/video0", "java", "-cp", "x", "Main");
        assertEquals(Set.of("java"), new CameraUsers(proc).programs());
        assertEquals(Set.of(), new CameraUsers(proc.resolve("missing")).programs());
    }

    private void process(String pid, String comm, String stat, String fdTarget, String... cmdline) throws IOException {
        Path dir = Files.createDirectories(proc.resolve(pid));
        Files.writeString(dir.resolve("comm"), comm + "\n");
        Files.writeString(dir.resolve("stat"), stat + " 0 0 0\n");
        Files.writeString(dir.resolve("cmdline"), String.join("\0", cmdline) + (cmdline.length > 0 ? "\0" : ""));
        Path fd = Files.createDirectories(dir.resolve("fd"));
        if (fdTarget != null) Files.createSymbolicLink(fd.resolve("3"), Path.of(fdTarget));
    }
}
