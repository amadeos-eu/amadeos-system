package org.datazuul.amadeos.lock;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import java.awt.GraphicsEnvironment;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.datazuul.amadeos.wm.x11.LibC;
import org.datazuul.amadeos.wm.x11.LibX11;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Runs the real lock process on the test display (Xvfb in CI) with mock PAM and types into it
 * through XTest, like a user at the keyboard.
 */
class ScreenLockIntegrationTest {
    // The wrapper script of the package (desktop/ is the working directory of the tests).
    private static final String WRAPPER = "packaging/root/usr/bin/amadeos-lock";
    private final LibX11 x11 = LibX11.INSTANCE;
    private Pointer display;
    private NativeLong root;
    private final List<Process> started = new ArrayList<>();

    @BeforeEach
    void connect() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs an X display");
        assumeFalse(LibC.INSTANCE.getuid() == 0, "mock PAM is refused for root");
        display = x11.XOpenDisplay(null);
        assumeFalse(display == null, "cannot open X display");
        root = x11.XRootWindow(display, x11.XDefaultScreen(display));
        assertFalse(LockSelection.isLocked(x11, display), "no lock may be active before the test");
    }

    @AfterEach
    void cleanUp() throws Exception {
        for (Process process : started) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
        if (display != null) {
            waitFor(() -> !LockSelection.isLocked(x11, display), 5000);
            x11.XCloseDisplay(display);
        }
    }

    @Test
    void locksInputUntilTheRightPasswordIsTyped() throws Exception {
        Process lock = start(lockCommand());
        assertTrue(waitFor(this::lockedAndGrabbed, 10_000), "lock must take the selection and grab input");

        Process second = start(lockCommand());
        assertTrue(second.waitFor(15, TimeUnit.SECONDS));
        assertEquals(ScreenLock.ALREADY_LOCKED, second.exitValue(), "a second lock must step aside");

        type("wrong");
        Thread.sleep(2500);
        assertTrue(lock.isAlive(), "a wrong password must not unlock");
        assertTrue(lockedAndGrabbed());

        type("amadeos");
        assertTrue(lock.waitFor(15, TimeUnit.SECONDS), "the right password must unlock");
        assertEquals(ScreenLock.UNLOCKED, lock.exitValue());
        assertTrue(waitFor(() -> !LockSelection.isLocked(x11, display) && canGrabKeyboard(), 5000));
    }

    @Test
    void wrapperLocksAgainWhenTheLockProcessIsKilled() throws Exception {
        assertTrue(new File(WRAPPER).canExecute(), "wrapper script must be executable");
        Process wrapper = start(List.of("sh", WRAPPER));
        assertTrue(waitFor(this::lockedAndGrabbed, 10_000));

        ProcessHandle first = lockJvm(wrapper);
        first.destroyForcibly();
        assertTrue(waitFor(() -> lockJvm(wrapper) != null && lockJvm(wrapper).pid() != first.pid()
                && lockedAndGrabbed(), 15_000), "a killed lock must be replaced by a new one");

        type("amadeos");
        assertTrue(wrapper.waitFor(15, TimeUnit.SECONDS), "unlocking must end the wrapper");
        assertEquals(0, wrapper.exitValue());
    }

    private List<String> lockCommand() {
        return List.of(javaBinary(), "--enable-native-access=ALL-UNNAMED", "-Duser.name=amadeos",
                "-cp", System.getProperty("java.class.path"), "org.datazuul.amadeos.wm.AmadeOsLauncher", "lock");
    }

    private Process start(List<String> command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command).inheritIO();
        Map<String, String> env = pb.environment();
        env.put("AMADEOS_MOCK_PAM", "1");
        env.put("AMADEOS_JAR", System.getProperty("java.class.path"));
        env.put("JAVA_HOME", System.getProperty("java.home"));
        env.put("JAVA_TOOL_OPTIONS", "-Duser.name=amadeos");
        Process process = pb.start();
        started.add(process);
        return process;
    }

    private static ProcessHandle lockJvm(Process wrapper) {
        return wrapper.descendants()
                .filter(p -> p.info().commandLine().orElse("").contains("AmadeOsLauncher lock"))
                .findFirst().orElse(null);
    }

    private boolean lockedAndGrabbed() {
        return LockSelection.isLocked(x11, display) && !canGrabKeyboard();
    }

    private boolean canGrabKeyboard() {
        int result = x11.XGrabKeyboard(display, root, 0, LibX11.GrabModeAsync, LibX11.GrabModeAsync,
                new NativeLong(LibX11.CurrentTime));
        if (result == LibX11.GrabSuccess) {
            x11.XUngrabKeyboard(display, new NativeLong(LibX11.CurrentTime));
            x11.XFlush(display);
            return true;
        }
        return false;
    }

    private static void type(String text) throws Exception {
        Robot robot = new Robot();
        for (char c : text.toCharArray()) {
            int key = KeyEvent.getExtendedKeyCodeForChar(c);
            robot.keyPress(key);
            robot.keyRelease(key);
            robot.delay(20);
        }
        robot.keyPress(KeyEvent.VK_ENTER);
        robot.keyRelease(KeyEvent.VK_ENTER);
    }

    private static boolean waitFor(BooleanSupplier condition, long millis) throws InterruptedException {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(50);
        }
        return condition.getAsBoolean();
    }

    private static String javaBinary() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }
}
