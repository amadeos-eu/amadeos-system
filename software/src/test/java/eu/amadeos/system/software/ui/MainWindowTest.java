package eu.amadeos.system.software.ui;

import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.system.packages.Change;
import eu.amadeos.system.packages.InstalledPackage;
import eu.amadeos.system.packages.PackageInfo;
import eu.amadeos.system.packages.PackageService;
import eu.amadeos.system.packages.Progress;
import eu.amadeos.system.packages.ProgressListener;
import eu.amadeos.system.packages.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static eu.amadeos.system.software.ui.UpdateTableModelTest.EN;
import static eu.amadeos.system.software.ui.UpdateTableModelTest.SECURITY;
import static eu.amadeos.system.software.ui.UpdateTableModelTest.STABLE;
import static eu.amadeos.system.software.ui.UpdateTableModelTest.update;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainWindowTest {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final FakeService service = new FakeService();
    private MainWindow window;

    @AfterEach
    void close() throws Exception {
        if (window != null) SwingUtilities.invokeAndWait(window::dispose);
        executor.shutdownNow();
    }

    private void open() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            window = new MainWindow(service, EN, executor, new EdtExecutor());
            window.start(false);
        });
    }

    @Test
    void theUpdatesTabListsTheWaitingUpdates() throws Exception {
        service.updates = List.of(update("bash", STABLE), update("openssl", SECURITY));
        open();
        await(() -> !onEdt(() -> window.updatesTab().model().updates().isEmpty()));
        assertEquals("Updates (2)", onEdt(window::updatesTabTitle));
        assertEquals("2 updates are waiting, one of them a security update.",
                onEdt(() -> window.updatesTab().summaryText()));
        // The security update is first and chosen, its description read.
        assertEquals("openssl", onEdt(() -> window.updatesTab().selectedUpdate().orElseThrow().name()));
        await(() -> onEdt(() -> window.updatesTab().details().summaryText().equals("Secure sockets")));
        assertTrue(onEdt(() -> window.updatesTab().installSecurityButton().isEnabled()));
    }

    @Test
    void installingSelectedUpdatesCallsTheServiceAndOffersARestart() throws Exception {
        service.updates = List.of(update("bash", STABLE), update("zsh", STABLE));
        service.result = new Result(Result.Status.DONE, "", true);
        open();
        await(() -> onEdt(() -> window.updatesTab().model().getRowCount()) == 2);
        assertFalse(onEdt(window::restartBarShown));
        assertFalse(onEdt(() -> window.updatesTab().installSecurityButton().isEnabled()));

        // All ticked: everything is upgraded.
        SwingUtilities.invokeAndWait(() -> window.updatesTab().installSelectedButton().doClick());
        await(() -> onEdt(window::restartBarShown));
        assertEquals("upgrade [] security=false", service.calls.get(1));
        assertEquals("The updates are installed.", onEdt(() -> window.operation().statusText()));
        assertTrue(onEdt(() -> window.operation().logText()).contains("Unpacking bash"));

        // One unticked: only the others.
        await(() -> service.calls.size() >= 3);
        SwingUtilities.invokeAndWait(() -> {
            window.updatesTab().model().setValueAt(false, 1, UpdateTableModel.SELECTED);
            window.updatesTab().installSelectedButton().doClick();
        });
        await(() -> service.calls.contains("upgrade [bash] security=false"));
    }

    @Test
    void anEmptyListSaysSoInTheTab() throws Exception {
        open();
        await(() -> service.calls.contains("upgrades"));
        await(() -> onEdt(() -> window.updatesTab().summaryText()).equals("Your software is up to date."));
        assertEquals("Updates", onEdt(window::updatesTabTitle));
        assertFalse(onEdt(() -> window.updatesTab().installSelectedButton().isEnabled()));
    }

    @Test
    void outcomesAreSaidPlainly() {
        assertEquals("Another program is installing software right now. Try again in a moment.",
                MainWindow.outcome(EN, new Result(Result.Status.LOCKED, "", false), true));
        assertEquals("Not done: the password was not given.",
                MainWindow.outcome(EN, new Result(Result.Status.NOT_AUTHORIZED, "", false), true));
        assertEquals("That did not work: no space left",
                MainWindow.outcome(EN, new Result(Result.Status.FAILED, "no space left", false), true));
        assertEquals("Done.", MainWindow.outcome(EN, new Result(Result.Status.DONE, "", false), false));
    }

    private static <T> T onEdt(Supplier<T> read) throws Exception {
        AtomicReference<T> value = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> value.set(read.get()));
        return value.get();
    }

    private interface Check {
        boolean test() throws Exception;
    }

    private static void await(Check condition) throws Exception {
        long end = System.nanoTime() + 5_000_000_000L;
        while (!condition.test()) {
            if (System.nanoTime() > end) throw new AssertionError("timed out");
            Thread.sleep(10);
        }
    }

    private static final class FakeService implements PackageService {
        final List<String> calls = new CopyOnWriteArrayList<>();
        volatile List<Change> updates = List.of();
        volatile Result result = new Result(Result.Status.DONE, "", false);

        @Override
        public List<Change> upgrades() {
            calls.add("upgrades");
            return updates;
        }

        @Override
        public List<Change> simulateInstall(List<String> names) {
            return List.of();
        }

        @Override
        public List<Change> simulateRemove(List<String> names) {
            return List.of();
        }

        @Override
        public List<InstalledPackage> installed() {
            return List.of();
        }

        @Override
        public List<PackageInfo> available() {
            return List.of();
        }

        @Override
        public Optional<PackageInfo> details(String name) {
            return Optional.of(new PackageInfo(name, "1.1", "amd64", "libs", "Secure sockets",
                    "Long description.", 1024, 512, "https://example.org", List.of(), List.of()));
        }

        @Override
        public List<SearchHit> search(String words) {
            return List.of();
        }

        @Override
        public Result refresh(ProgressListener listener) {
            calls.add("refresh");
            return result;
        }

        @Override
        public Result upgrade(List<String> names, boolean securityOnly, ProgressListener listener) {
            calls.add("upgrade " + names + " security=" + securityOnly);
            listener.progress(new Progress(Progress.Phase.INSTALL, 50, "bash", "Unpacking bash"));
            listener.message("Unpacking bash (1.1) over (1.0) ...");
            return result;
        }

        @Override
        public Result install(List<String> names, ProgressListener listener) {
            return result;
        }

        @Override
        public Result remove(List<String> names, ProgressListener listener) {
            return result;
        }

        @Override
        public AutoCloseable watch(Runnable onChange) {
            return () -> {};
        }

        @Override
        public Optional<Instant> lastRefresh() {
            return Optional.of(Instant.parse("2026-10-21T08:00:00Z"));
        }

        @Override
        public boolean automaticSecurityUpdates() {
            return false;
        }

        @Override
        public Result setAutomaticSecurityUpdates(boolean on, ProgressListener listener) {
            calls.add("automatic " + on);
            return result;
        }
    }
}
