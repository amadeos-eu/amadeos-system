package eu.amadeos.system.panel.cde.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.packages.Change;
import eu.amadeos.system.packages.InstalledPackage;
import eu.amadeos.system.packages.Origin;
import eu.amadeos.system.packages.PackageInfo;
import eu.amadeos.system.packages.PackageService;
import eu.amadeos.system.packages.ProgressListener;
import eu.amadeos.system.packages.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdatesCellTest {
    private static final Messages EN = Messages.load(UpdatesCell.class, NetworkCell.DESKTOP_TEXTS, Locale.ENGLISH);
    private static final Origin STABLE = new Origin("Debian", "13.1", "stable");
    private static final Origin SECURITY = new Origin("Debian-Security", "13", "stable-security");

    private static Change upgrade(String name, Origin origin) {
        return new Change(Change.Kind.UPGRADE, name, "1.0", "1.1", List.of(origin), "amd64");
    }

    private static final List<Change> PENDING = List.of(upgrade("tzdata", STABLE), upgrade("openssl", SECURITY),
            upgrade("curl", STABLE), upgrade("libssl3t64", SECURITY), upgrade("bash", STABLE));

    private final List<String> notified = new ArrayList<>();

    private UpdatesCell cell(PackageService service, Runnable showUpdates) {
        return new UpdatesCell(service, showUpdates, null, EN, (message, warning) -> notified.add(message));
    }

    @Test
    void shownOnlyWhileUpdatesAreWaiting() {
        UpdatesCell cell = cell(new FakeService(), null);
        List<Boolean> relayouts = new ArrayList<>();
        cell.onAvailabilityChanged(() -> relayouts.add(cell.available()));
        assertFalse(cell.available());
        assertEquals("The system is up to date", cell.describe());

        cell.show(PENDING);
        assertTrue(cell.available());
        assertEquals("5 updates, 2 security", cell.component().getToolTipText());
        cell.show(PENDING);
        cell.show(List.of(upgrade("bash", STABLE)));
        assertEquals("1 update", cell.describe());
        cell.show(List.of());
        assertEquals(List.of(true, false), relayouts, "the slot lays out again only when the cell appears or goes");
    }

    @Test
    void menuListsSecurityUpdatesFirstAndOffersSecurityOnly() {
        UpdatesCell cell = cell(new FakeService(), null);
        cell.show(PENDING);
        List<String> texts = items(cell.buildMenu()).stream().map(JMenuItem::getText).toList();
        assertEquals(List.of("5 updates, 2 security",
                "openssl  1.0 → 1.1", "libssl3t64  1.0 → 1.1", "tzdata  1.0 → 1.1", "curl  1.0 → 1.1",
                "bash  1.0 → 1.1",
                "Install all updates", "Install security updates only", "Check now"), texts);
        assertEquals(UpdatesCell.SECURITY, items(cell.buildMenu()).get(1).getForeground());
    }

    @Test
    void longListsAreCutAndShowUpdatesOpensTheWindow() {
        List<Boolean> opened = new ArrayList<>();
        UpdatesCell cell = cell(new FakeService(), () -> opened.add(true));
        List<Change> many = new ArrayList<>();
        for (int i = 0; i < 12; i++) many.add(upgrade("pkg" + i, STABLE));
        cell.show(many);
        List<JMenuItem> items = items(cell.buildMenu());
        List<String> texts = items.stream().map(JMenuItem::getText).toList();
        assertEquals("… and 4 more", texts.get(1 + UpdatesCell.MENU_ENTRIES));
        assertFalse(texts.contains("Install security updates only"), "no security updates, no such entry");
        assertTrue(texts.contains("Show updates…"));
        items.get(1).doClick();
        assertEquals(List.of(true), opened, "an entry opens the update window");
    }

    @Test
    void installAllRunsTheHelperAndReadsAgain() throws Exception {
        FakeService service = new FakeService();
        service.result = new Result(Result.Status.DONE, "", true);
        UpdatesCell cell = cell(service, null);
        cell.show(PENDING);
        cell.install(false);
        assertEquals(UpdatesCell.Busy.INSTALLING, cell.busy());
        assertFalse(items(cell.buildMenu()).stream().filter(i -> i.getText().equals("Install all updates"))
                .findFirst().orElseThrow().isEnabled(), "no second install while one runs");

        assertEquals("upgrade all", service.calls.poll(5, TimeUnit.SECONDS));
        // The cell reads the pending updates again once the helper is done
        assertEquals("upgrades", service.calls.poll(5, TimeUnit.SECONDS));
        awaitIdle(cell);
        assertEquals(UpdatesCell.Busy.NONE, cell.busy());
        assertEquals(List.of(), cell.updates());
        assertFalse(cell.available());
        assertEquals(List.of("The updates are installed. Restart the computer to complete them."), notified);
        cell.stop();
    }

    @Test
    void securityOnlyAndCheckNow() throws Exception {
        FakeService service = new FakeService();
        UpdatesCell cell = cell(service, null);
        cell.show(PENDING);
        cell.install(true);
        assertEquals("upgrade security", service.calls.poll(5, TimeUnit.SECONDS));
        service.calls.poll(5, TimeUnit.SECONDS);
        awaitIdle(cell);
        cell.check();
        assertEquals("refresh", service.calls.poll(5, TimeUnit.SECONDS));
        service.calls.poll(5, TimeUnit.SECONDS);
        awaitIdle(cell);
        assertEquals(1, notified.size(), "a refresh that worked needs no message");
        cell.stop();
    }

    @Test
    void messagesAfterTheOperation() {
        UpdatesCell cell = cell(new FakeService(), null);
        assertEquals("The updates are installed.",
                cell.resultMessage(new Result(Result.Status.DONE, "", false), true));
        assertEquals(null, cell.resultMessage(new Result(Result.Status.DONE, "", false), false));
        assertEquals("Another update is running. Try again when it has finished.",
                cell.resultMessage(new Result(Result.Status.LOCKED, "", false), true));
        assertEquals(null, cell.resultMessage(new Result(Result.Status.NOT_AUTHORIZED, "", false), true),
                "closing the password dialog is the user's choice");
        assertEquals("The package lists could not be refreshed:\nTemporary failure resolving 'deb.debian.org'",
                cell.resultMessage(new Result(Result.Status.FAILED,
                        "Temporary failure resolving 'deb.debian.org'", false), false));
    }

    /** Waits until the cell has handled the end of its operation on the event dispatch thread. */
    private static void awaitIdle(UpdatesCell cell) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        UpdatesCell.Busy[] busy = new UpdatesCell.Busy[1];
        do {
            SwingUtilities.invokeAndWait(() -> busy[0] = cell.busy());
        } while (busy[0] != UpdatesCell.Busy.NONE && System.nanoTime() < end);
    }

    private static List<JMenuItem> items(JPopupMenu menu) {
        return Arrays.stream(menu.getComponents())
                .filter(JMenuItem.class::isInstance).map(JMenuItem.class::cast).toList();
    }

    /** Answers with nothing pending and records the operations. */
    private static final class FakeService implements PackageService {
        final LinkedBlockingQueue<String> calls = new LinkedBlockingQueue<>();
        Result result = new Result(Result.Status.DONE, "", false);

        @Override
        public List<Change> upgrades() {
            calls.add("upgrades");
            return List.of();
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
            return Optional.empty();
        }

        @Override
        public List<SearchHit> search(String words) {
            return List.of();
        }

        @Override
        public AutoCloseable watch(Runnable onChange) {
            return () -> {};
        }

        @Override
        public Optional<java.time.Instant> lastRefresh() {
            return Optional.empty();
        }

        @Override
        public boolean automaticSecurityUpdates() {
            return false;
        }

        @Override
        public Result setAutomaticSecurityUpdates(boolean on, ProgressListener listener) {
            return result;
        }

        @Override
        public Result refresh(ProgressListener listener) {
            calls.add("refresh");
            return result;
        }

        @Override
        public Result upgrade(List<String> names, boolean securityOnly, ProgressListener listener) {
            calls.add(securityOnly ? "upgrade security" : "upgrade all");
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
    }
}
