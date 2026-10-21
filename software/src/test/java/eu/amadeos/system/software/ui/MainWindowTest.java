package eu.amadeos.system.software.ui;

import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.system.packages.CatalogEntry;
import eu.amadeos.system.packages.Category;
import eu.amadeos.system.packages.Change;
import eu.amadeos.system.packages.InstalledPackage;
import eu.amadeos.system.packages.PackageInfo;
import eu.amadeos.system.packages.PackageService;
import eu.amadeos.system.packages.Progress;
import eu.amadeos.system.packages.ProgressListener;
import eu.amadeos.system.packages.Result;
import eu.amadeos.system.software.appstream.AppData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainWindowTest {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final FakeService service = new FakeService();
    private volatile Map<String, AppData> apps = Map.of();
    @TempDir
    Path cache;
    private MainWindow window;

    @AfterEach
    void close() throws Exception {
        if (window != null) SwingUtilities.invokeAndWait(window::dispose);
        executor.shutdownNow();
    }

    private void open() throws Exception {
        open(false);
    }

    private void open(boolean updates) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            window = new MainWindow(service, EN, executor, new EdtExecutor(), new DesktopLauncher(), () -> apps,
                    new ScreenshotCache(cache));
            window.start(updates, false);
        });
    }

    private static CatalogEntry entry(String name, Category category, String installed, String available,
                                      boolean automatic) {
        return new CatalogEntry(name, name + " summary", "misc", category, installed, available, automatic);
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
        assertTrue(service.calls.contains("upgrade [] security=false"));
        assertEquals("The updates are installed.", onEdt(() -> window.operation().statusText()));
        assertTrue(onEdt(() -> window.operation().logText()).contains("Unpacking bash"));

        // One unticked: only the others.
        await(() -> service.calls.stream().filter("upgrades"::equals).count() >= 2);
        SwingUtilities.invokeAndWait(() -> {
            window.updatesTab().model().setValueAt(false, 1, UpdateTableModel.SELECTED);
            window.updatesTab().installSelectedButton().doClick();
        });
        await(() -> service.calls.contains("upgrade [bash] security=false"));
    }

    @Test
    void anEmptyListSaysSoInTheTabWhenItIsAskedFor() throws Exception {
        open(true);
        await(() -> service.calls.contains("upgrades"));
        await(() -> onEdt(() -> window.updatesTab().summaryText()).equals("Your software is up to date."));
        assertEquals("Updates", onEdt(window::updatesTabTitle));
        assertFalse(onEdt(() -> window.updatesTab().installSelectedButton().isEnabled()));
    }

    @Test
    void withoutUpdatesThereIsNoUpdatesTab() throws Exception {
        open();
        await(() -> service.calls.contains("catalog"));
        await(() -> !onEdt(() -> window.operation().busy()));
        assertEquals(List.of("Applications", "Command line", "Other"), onEdt(window::tabTitles));
        assertNull(onEdt(window::updatesTabTitle));
    }

    @Test
    void theCatalogIsSortedIntoTabsAndSearchedTogether() throws Exception {
        service.catalog = List.of(entry("fonts-x", Category.OTHER, "", "1", false),
                entry("gimp", Category.APPLICATION, "", "2.10", false),
                entry("libfoo1", Category.LIBRARY, "1.0", "1.0", true),
                entry("vim", Category.COMMAND_LINE, "9.0", "9.1", false));
        open();
        await(() -> onEdt(() -> window.catalogTab(Category.APPLICATION).shown()) == 1);
        assertEquals(1, onEdt(() -> window.catalogTab(Category.COMMAND_LINE).shown()));
        assertEquals(1, onEdt(() -> window.catalogTab(Category.LIBRARY).shown()));

        // A search narrows all tabs, counts their hits and moves to a tab with some.
        SwingUtilities.invokeAndWait(() -> window.searchField().setText("vim"));
        await(() -> onEdt(window::tabTitles).contains("Command line (1)"));
        assertEquals(List.of("Applications (0)", "Command line (1)", "Other (0)"), onEdt(window::tabTitles));
        assertEquals("vim", onEdt(() -> window.catalogTab(Category.COMMAND_LINE).selected().orElseThrow().name()));
        assertEquals("Installed: version 9.0. Version 9.1 is available.",
                onEdt(() -> window.catalogTab(Category.COMMAND_LINE).details().stateText()));

        SwingUtilities.invokeAndWait(() -> {
            window.searchField().setText("");
            window.showFilter().setSelectedIndex(1);
            window.librariesSwitch().doClick();
        });
        await(() -> onEdt(window::tabTitles).size() == 4);
        assertEquals(List.of("Applications (0)", "Command line (1)", "Other (0)", "Libraries (1)"),
                onEdt(window::tabTitles));
    }

    @Test
    void applicationsShowTheirAppStreamNameAndAreFoundByIt() throws Exception {
        apps = Map.of("gimp", new AppData("org.gimp.GIMP", "gimp", "GNU Image Manipulation Program",
                "Create images and edit photographs", "<p>GIMP is <em>free</em>.</p>", List.of("Graphics"),
                Optional.empty(), "https://www.gimp.org/", List.of()));
        service.catalog = List.of(entry("abiword", Category.APPLICATION, "", "3.0", false),
                entry("gimp", Category.APPLICATION, "", "2.10", false));
        open();
        // The catalog first, the AppStream names when they have been read.
        await(() -> onEdt(() -> {
            CatalogTableModel model = window.catalogTab(Category.APPLICATION).model();
            return model.getRowCount() == 2 && model.getValueAt(1, CatalogTableModel.NAME)
                    .equals("GNU Image Manipulation Program");
        }));
        assertEquals("abiword", onEdt(() -> window.catalogTab(Category.APPLICATION).model()
                .getValueAt(0, CatalogTableModel.NAME)));
        assertEquals("Create images and edit photographs",
                onEdt(() -> window.catalogTab(Category.APPLICATION).model().getValueAt(1, CatalogTableModel.SUMMARY)));

        // Groups: gimp from its AppStream categories, abiword from its section.
        assertEquals(List.of(new CatalogTab.GroupRow(null, 2), new CatalogTab.GroupRow(Group.GRAPHICS, 1),
                new CatalogTab.GroupRow(Group.OTHER, 1)), onEdt(() -> window.catalogTab(Category.APPLICATION).groupRows()));
        SwingUtilities.invokeAndWait(() -> window.catalogTab(Category.APPLICATION).selectGroup(Group.GRAPHICS));
        assertEquals(1, onEdt(() -> window.catalogTab(Category.APPLICATION).model().getRowCount()));
        SwingUtilities.invokeAndWait(() -> window.catalogTab(Category.APPLICATION).selectGroup(null));

        SwingUtilities.invokeAndWait(() -> window.searchField().setText("manipulation"));
        await(() -> onEdt(window::tabTitles).contains("Applications (1)"));
        assertEquals("GNU Image Manipulation Program",
                onEdt(() -> window.catalogTab(Category.APPLICATION).details().titleText()));
        assertEquals(List.of(new CatalogTab.GroupRow(null, 1), new CatalogTab.GroupRow(Group.GRAPHICS, 1)),
                onEdt(() -> window.catalogTab(Category.APPLICATION).groupRows()));
    }

    @Test
    void installUpdateAndRemoveGoThroughTheService() throws Exception {
        service.catalog = List.of(entry("gimp", Category.APPLICATION, "", "2.10", false),
                entry("vim", Category.COMMAND_LINE, "9.0", "9.1", false));
        open();
        await(() -> onEdt(() -> window.catalogTab(Category.APPLICATION).shown()) == 1);
        SwingUtilities.invokeAndWait(() -> {
            CatalogTab tab = window.catalogTab(Category.APPLICATION);
            tab.select("gimp");
            assertTrue(tab.details().installButton().isEnabled());
            assertFalse(tab.details().removeButton().isEnabled());
            tab.details().installButton().doClick();
        });
        // gimp needs nothing else, so no question.
        await(() -> service.calls.contains("install [gimp]"));
        await(() -> onEdt(() -> window.operation().statusText()).equals("gimp is installed."));

        SwingUtilities.invokeAndWait(() -> {
            CatalogTab tab = window.catalogTab(Category.COMMAND_LINE);
            tab.select("vim");
            tab.details().updateButton().doClick();
        });
        await(() -> service.calls.contains("upgrade [vim] security=false"));
    }

    @Test
    void confirmationsNameWhatElseChanges() throws Exception {
        open();
        Change dependency = new Change(Change.Kind.INSTALL, "libgimp2.0", "", "2.10", List.of(STABLE), "amd64");
        Change itself = new Change(Change.Kind.INSTALL, "gimp", "", "2.10", List.of(STABLE), "amd64");
        assertEquals("", onEdt(() -> window.confirmation("gimp", List.of(itself), false)));
        assertEquals("gimp needs one more package: libgimp2.0",
                onEdt(() -> window.confirmation("gimp", List.of(dependency, itself), false)));
        List<Change> removals = new java.util.ArrayList<>();
        for (int i = 0; i < 14; i++) {
            removals.add(new Change(Change.Kind.REMOVE, "pkg" + i + ":amd64", "1", "", List.of(), ""));
        }
        assertEquals("This also removes 14 packages that need it: pkg0, pkg1, pkg2, pkg3, pkg4, pkg5, pkg6, "
                + "pkg7, pkg8, pkg9, pkg10, pkg11, and 2 more", onEdt(() -> window.confirmation("x", removals, true)));
    }

    @Test
    void outcomesAreSaidPlainly() {
        assertEquals("Another program is installing software right now. Try again in a moment.",
                MainWindow.outcome(EN, new Result(Result.Status.LOCKED, "", false), "x"));
        assertEquals("Not done: the password was not given.",
                MainWindow.outcome(EN, new Result(Result.Status.NOT_AUTHORIZED, "", false), "x"));
        assertEquals("That did not work: no space left",
                MainWindow.outcome(EN, new Result(Result.Status.FAILED, "no space left", false), "x"));
        assertEquals("gimp is installed.", MainWindow.outcome(EN, new Result(Result.Status.DONE, "", false),
                EN.format("outcome.package-installed", "gimp")));
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
        volatile List<CatalogEntry> catalog = List.of();
        volatile Result result = new Result(Result.Status.DONE, "", false);

        @Override
        public List<Change> upgrades() {
            calls.add("upgrades");
            return updates;
        }

        @Override
        public List<Change> simulateInstall(List<String> names) {
            return names.stream().map(n -> new Change(Change.Kind.INSTALL, n, "", "1", List.of(STABLE), "amd64"))
                    .toList();
        }

        @Override
        public List<CatalogEntry> catalog() {
            calls.add("catalog");
            return catalog;
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
            calls.add("install " + names);
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
