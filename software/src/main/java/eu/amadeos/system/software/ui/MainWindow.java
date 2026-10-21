package eu.amadeos.system.software.ui;

import com.google.inject.Inject;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.system.packages.CatalogEntry;
import eu.amadeos.system.packages.Category;
import eu.amadeos.system.packages.Change;
import eu.amadeos.system.packages.PackageService;
import eu.amadeos.system.packages.Progress;
import eu.amadeos.system.packages.ProgressListener;
import eu.amadeos.system.packages.Result;
import eu.amadeos.system.software.appstream.AppData;
import eu.amadeos.system.software.appstream.AppStreamSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The Software window: the Updates tab while updates wait, then the catalog in tabs (Applications, Command
 * line, Other, and Libraries when asked for), with one search and an All / Installed filter over all of them.
 * apt is asked on the background executor through {@link PackageService}; one operation runs at a time and
 * shows its progress in the bar at the bottom.
 */
public final class MainWindow extends JFrame {
    private static final Logger LOG = LoggerFactory.getLogger(MainWindow.class);
    static final Color RESTART_BACKGROUND = new Color(0xF4, 0xE2, 0x8C);
    /** At most this many package names in a confirmation. */
    private static final int LISTED = 12;
    private static final Map<Category, String> TAB_KEYS = Map.of(Category.APPLICATION, "tab.applications",
            Category.COMMAND_LINE, "tab.command-line", Category.OTHER, "tab.other", Category.LIBRARY, "tab.libraries");

    private final PackageService packages;
    private final Messages msg;
    private final ExecutorService executor;
    private final EdtExecutor edt;
    private final DesktopLauncher launcher;
    private final AppStreamSource appStream;
    private final ScreenshotCache screenshots;

    private final JTabbedPane tabs = new JTabbedPane();
    private final UpdatesTab updatesTab;
    private final Map<Category, CatalogTab> catalogTabs = new EnumMap<>(Category.class);
    private final OperationBar operation;
    private final JPanel restartBar = new JPanel(new BorderLayout(8, 0));
    private final JTextField search = new JTextField(24);
    private final JComboBox<String> show;
    private final JCheckBox libraries;
    private final Timer searchDelay;
    private final Action check;
    private final Action settings;
    private boolean busy;
    private boolean catalogRead;
    private List<CatalogEntry> catalog = List.of();
    /** The package lists were fetched once already because there were none (a new image ships without them). */
    private boolean listsFetched;
    private Map<String, AppData> apps = Map.of();

    @Inject
    public MainWindow(PackageService packages, Messages msg, ExecutorService executor, EdtExecutor edt,
                      DesktopLauncher launcher, AppStreamSource appStream, ScreenshotCache screenshots) {
        super(msg.get("app.title"));
        this.packages = packages;
        this.msg = msg;
        this.executor = executor;
        this.edt = edt;
        this.launcher = launcher;
        this.appStream = appStream;
        this.screenshots = screenshots;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        updatesTab = new UpdatesTab(msg, this::loadDetails, this::installUpdates, this::installSecurityUpdates);
        for (Category category : List.of(Category.APPLICATION, Category.COMMAND_LINE, Category.OTHER,
                Category.LIBRARY)) {
            catalogTabs.put(category, new CatalogTab(msg, category, this::loadScreenshot, this::browse,
                    this::loadDetails, this::install, this::remove, this::update));
        }
        operation = new OperationBar(msg);

        check = action("action.check", KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), this::checkNow);
        settings = action("action.settings", null, this::openSettings);
        Action exit = action("action.exit", KeyStroke.getKeyStroke(KeyEvent.VK_Q, KeyEvent.CTRL_DOWN_MASK),
                this::dispose);
        Action find = action("action.search", KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK),
                () -> {
                    search.requestFocusInWindow();
                    search.selectAll();
                });
        JMenuBar bar = new JMenuBar();
        JMenu software = Mnemonics.apply(new JMenu(), msg.get("menu.software"));
        software.add(check);
        software.add(find);
        software.add(settings);
        software.addSeparator();
        software.add(exit);
        bar.add(software);
        setJMenuBar(bar);

        restartBar.setBackground(RESTART_BACKGROUND);
        restartBar.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        JLabel restartText = new JLabel(msg.get("restart.needed"));
        restartText.setForeground(Color.BLACK);
        JButton restartNow = new JButton(msg.get("action.restart"));
        restartNow.addActionListener(e -> restart());
        restartBar.add(restartText, BorderLayout.CENTER);
        restartBar.add(restartNow, BorderLayout.EAST);
        restartBar.setVisible(false);

        // One search and filter for all catalog tabs.
        show = new JComboBox<>(new String[]{msg.get("filter.all"), msg.get("filter.installed")});
        show.addActionListener(e -> applyFilter());
        libraries = new JCheckBox(msg.get("filter.libraries"));
        libraries.setToolTipText(msg.get("filter.libraries.tip"));
        libraries.addActionListener(e -> updateTabs());
        searchDelay = new Timer(250, e -> applyFilter());
        searchDelay.setRepeats(false);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                searchDelay.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                searchDelay.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                searchDelay.restart();
            }
        });
        search.addActionListener(e -> applyFilter());
        JLabel searchLabel = new JLabel(msg.get("filter.search"));
        searchLabel.setLabelFor(search);
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        filterBar.add(searchLabel);
        filterBar.add(search);
        filterBar.add(show);
        filterBar.add(libraries);

        JPanel north = new JPanel(new BorderLayout());
        north.add(restartBar, BorderLayout.NORTH);
        north.add(filterBar, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        add(operation, BorderLayout.SOUTH);
        updateTabs();

        setSize(new Dimension(1080, 660));
        setLocationRelativeTo(null);
    }

    /**
     * Reads the waiting updates and the catalog. {@code updates} opens the Updates tab even when nothing waits
     * (it then says so), {@code openSettings} the settings dialog as well.
     */
    public void start(boolean updates, boolean openSettings) {
        if (updates) {
            showUpdatesTab(true);
            tabs.setSelectedComponent(updatesTab);
        }
        reload();
        readAppStream();
        if (openSettings) openSettings();
    }

    /** Names, icons, descriptions and screenshots of the applications; read after the catalog, as it takes a while. */
    void readAppStream() {
        background(appStream::applications, read -> {
            apps = read;
            showItems();
        }, error -> LOG.warn("Cannot read the AppStream data", error));
    }

    /** Reads the waiting updates, the time of the last check and the catalog again. */
    void reload() {
        background(() -> new Snapshot(packages.upgrades(), packages.lastRefresh()), this::showUpdates,
                error -> operation.end(msg.format("error.read", message(error))));
        if (!catalogRead) operation.begin(msg.get("progress.reading"));
        background(packages::catalog, this::showCatalog, error -> {
            operation.end(msg.format("error.catalog", message(error)));
            LOG.warn("Cannot read the catalog", error);
        });
    }

    record Snapshot(List<Change> updates, Optional<Instant> lastRefresh) {
    }

    void showUpdates(Snapshot snapshot) {
        updatesTab.setUpdates(snapshot.updates(), snapshot.lastRefresh());
        if (snapshot.lastRefresh().isEmpty() && !listsFetched && !busy) {
            // Without lists the catalog holds only the installed packages; fetching needs no password.
            listsFetched = true;
            perform(msg.get("progress.lists"), packages::refresh, msg.get("outcome.done"));
        }
        int count = snapshot.updates().size();
        // The tab stays while it is looked at, so "up to date" is seen after installing.
        showUpdatesTab(count > 0 || tabs.getSelectedComponent() == updatesTab);
        int index = tabs.indexOfComponent(updatesTab);
        if (index >= 0) {
            tabs.setTitleAt(index, count == 0 ? msg.get("tab.updates") : msg.format("tab.updates.count", count));
        }
    }

    void showCatalog(List<CatalogEntry> read) {
        catalog = read;
        if (!catalogRead && !busy) operation.end(" ");
        catalogRead = true;
        showItems();
    }

    /** The catalog in its tabs, each package with its application's AppStream data, sorted by name. */
    private void showItems() {
        Map<Category, List<Item>> byCategory = new EnumMap<>(Category.class);
        for (Category category : Category.values()) byCategory.put(category, new ArrayList<>());
        for (CatalogEntry entry : catalog) {
            byCategory.get(entry.category()).add(new Item(entry, Optional.ofNullable(apps.get(entry.name()))));
        }
        Comparator<Item> byTitle = Comparator.comparing((Item i) -> i.title().toLowerCase(Locale.ROOT))
                .thenComparing(Item::name);
        catalogTabs.forEach((category, tab) -> tab.setItems(byCategory.get(category).stream().sorted(byTitle).toList()));
        updateTabTitles();
    }

    private void loadScreenshot(java.net.URI image, Consumer<java.nio.file.Path> done, Consumer<Throwable> failed) {
        background(() -> screenshots.get(image), done, error -> {
            LOG.info("No screenshot {}: {}", image, error.toString());
            failed.accept(error);
        });
    }

    /** A link in the details: the home page, in the web browser. */
    private void browse(String uri) {
        if (!uri.startsWith("https://") && !uri.startsWith("http://")) return;
        try {
            launcher.browse(uri);
        } catch (IOException e) {
            operation.end(msg.format("error.browse", e.getMessage()));
        }
    }

    private void showUpdatesTab(boolean shown) {
        int index = tabs.indexOfComponent(updatesTab);
        if (shown && index < 0) {
            tabs.insertTab(msg.get("tab.updates"), null, updatesTab, null, 0);
        } else if (!shown && index >= 0) {
            tabs.removeTabAt(index);
        }
    }

    /** The catalog tabs in their order; Libraries only with the switch. */
    private void updateTabs() {
        for (CatalogTab tab : catalogTabs.values()) {
            boolean wanted = tab.category() != Category.LIBRARY || libraries.isSelected();
            int index = tabs.indexOfComponent(tab);
            if (wanted && index < 0) {
                tabs.addTab(msg.get(TAB_KEYS.get(tab.category())), tab);
            } else if (!wanted && index >= 0) {
                tabs.removeTabAt(index);
            }
        }
        if (libraries.isSelected()) tabs.setSelectedComponent(catalogTabs.get(Category.LIBRARY));
        updateTabTitles();
    }

    private void applyFilter() {
        searchDelay.stop();
        CatalogFilter filter = CatalogFilter.of(search.getText(), show.getSelectedIndex() == 1);
        catalogTabs.values().forEach(tab -> tab.setFilter(filter));
        updateTabTitles();
        // A search shows the first tab with hits, unless the current one has some.
        if (!filter.words().isEmpty() && tabs.getSelectedComponent() instanceof CatalogTab current
                && current.shown() == 0) {
            for (int i = 0; i < tabs.getTabCount(); i++) {
                if (tabs.getComponentAt(i) instanceof CatalogTab tab && tab.shown() > 0) {
                    tabs.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    /** With a search or the Installed filter, each title says how many packages it shows. */
    private void updateTabTitles() {
        boolean filtered = !search.getText().isBlank() || show.getSelectedIndex() == 1;
        for (CatalogTab tab : catalogTabs.values()) {
            int index = tabs.indexOfComponent(tab);
            if (index < 0) continue;
            String name = msg.get(TAB_KEYS.get(tab.category()));
            tabs.setTitleAt(index, filtered && catalogRead ? msg.format("tab.count", name, tab.shown()) : name);
        }
    }

    private void loadDetails(Change update) {
        background(() -> packages.details(update.name()), info -> updatesTab.showDetails(update, info),
                error -> LOG.debug("No details for {}", update.name(), error));
    }

    private void loadDetails(CatalogEntry entry) {
        background(() -> packages.details(entry.name()),
                info -> catalogTabs.get(entry.category()).showDetails(entry, info),
                error -> LOG.debug("No details for {}", entry.name(), error));
    }

    private void installUpdates(List<String> names) {
        perform(msg.get("progress.installing"), listener -> packages.upgrade(names, false, listener),
                msg.get("outcome.installed"));
    }

    private void installSecurityUpdates() {
        perform(msg.get("progress.installing"), listener -> packages.upgrade(List.of(), true, listener),
                msg.get("outcome.installed"));
    }

    /** Installs a package once the user has agreed to what else it brings (or removes). */
    void install(CatalogEntry entry) {
        background(() -> packages.simulateInstall(List.of(entry.name())), changes -> {
            String others = confirmation(entry.name(), changes, false);
            if (!others.isEmpty() && JOptionPane.showConfirmDialog(this,
                    others + "\n\n" + msg.format("confirm.install", entry.name()), getTitle(),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) {
                return;
            }
            perform(msg.format("progress.installing-package", entry.name()),
                    listener -> packages.install(List.of(entry.name()), listener),
                    msg.format("outcome.package-installed", entry.name()));
        }, error -> operation.end(msg.format("error.simulate", message(error))));
    }

    /** Removes a package once the user has agreed, having seen what else goes with it. */
    void remove(CatalogEntry entry) {
        background(() -> packages.simulateRemove(List.of(entry.name())), changes -> {
            String question = msg.format("confirm.remove", entry.name());
            String others = confirmation(entry.name(), changes, true);
            if (!others.isEmpty()) question += "\n\n" + others;
            if (JOptionPane.showConfirmDialog(this, question, getTitle(), JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
                return;
            }
            perform(msg.format("progress.removing", entry.name()),
                    listener -> packages.remove(List.of(entry.name()), listener),
                    msg.format("outcome.removed", entry.name()));
        }, error -> operation.end(msg.format("error.simulate", message(error))));
    }

    void update(CatalogEntry entry) {
        perform(msg.format("progress.updating", entry.name()),
                listener -> packages.upgrade(List.of(entry.name()), false, listener),
                msg.format("outcome.updated", entry.name()));
    }

    /**
     * What a change does besides the package itself, as lines for a confirmation: the other packages it
     * installs and those it removes. Empty when it touches nothing else.
     */
    String confirmation(String name, List<Change> changes, boolean removing) {
        List<String> installs = new ArrayList<>();
        List<String> removes = new ArrayList<>();
        for (Change c : changes) {
            String plain = c.name().contains(":") ? c.name().substring(0, c.name().indexOf(':')) : c.name();
            if (plain.equals(name)) continue;
            if (c.kind() == Change.Kind.REMOVE) {
                removes.add(plain);
            } else if (c.kind() == Change.Kind.INSTALL) {
                installs.add(plain);
            }
        }
        List<String> lines = new ArrayList<>();
        if (!installs.isEmpty()) {
            lines.add(msg.format("confirm.installs-too", name, installs.size(), names(installs)));
        }
        if (!removes.isEmpty()) {
            lines.add(msg.format(removing ? "confirm.removes-too" : "confirm.install-removes", removes.size(),
                    names(removes)));
        }
        return String.join("\n\n", lines);
    }

    private String names(List<String> names) {
        if (names.size() <= LISTED) return String.join(", ", names);
        return String.join(", ", names.subList(0, LISTED)) + ", " + msg.format("confirm.more", names.size() - LISTED);
    }

    private void checkNow() {
        if (busy) return;
        perform(msg.get("progress.checking"), packages::refresh, msg.get("outcome.done"));
    }

    private void openSettings() {
        background(packages::automaticSecurityUpdates, automatic -> new SettingsDialog(this, msg, automatic,
                on -> perform(msg.get("progress.settings"),
                        listener -> packages.setAutomaticSecurityUpdates(on, listener), msg.get("outcome.done")))
                .setVisible(true),
                error -> operation.end(msg.format("error.read", message(error))));
    }

    /**
     * Runs one change through the helper: progress and apt's messages in the bar, the outcome in its status
     * line (and in a dialog when it failed), the updates and the catalog read again afterwards.
     */
    void perform(String title, Function<ProgressListener, Result> work, String doneText) {
        if (busy) return;
        setBusy(true);
        operation.begin(title);
        ProgressListener listener = new ProgressListener() {
            @Override
            public void progress(Progress progress) {
                edt.execute(() -> operation.progress(progress));
            }

            @Override
            public void message(String line) {
                edt.execute(() -> operation.message(line));
            }
        };
        background(() -> work.apply(listener), result -> finished(result, doneText),
                error -> finished(new Result(Result.Status.FAILED, message(error), false), doneText));
    }

    private void finished(Result result, String doneText) {
        setBusy(false);
        // New package lists bring new AppStream data, an installed package its desktop entry and metainfo.
        readAppStream();
        operation.end(outcome(msg, result, doneText));
        if (result.rebootRequired()) restartBar.setVisible(true);
        if (result.status() == Result.Status.FAILED) {
            operation.showLog(true);
            JOptionPane.showMessageDialog(this, outcome(msg, result, doneText), getTitle(),
                    JOptionPane.ERROR_MESSAGE);
        }
        reload();
    }

    /** The status line after an operation. */
    static String outcome(Messages msg, Result result, String doneText) {
        return switch (result.status()) {
            case DONE -> doneText;
            case LOCKED -> msg.get("outcome.locked");
            case NOT_AUTHORIZED -> msg.get("outcome.not-authorized");
            case FAILED -> msg.format("outcome.failed", result.message());
        };
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        updatesTab.setBusy(busy);
        catalogTabs.values().forEach(tab -> tab.setBusy(busy));
        check.setEnabled(!busy);
        settings.setEnabled(!busy);
    }

    /** Restarts the computer through logind, which lets the user of the active session do it. */
    private void restart() {
        if (JOptionPane.showConfirmDialog(this, msg.get("restart.confirm"), getTitle(), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            new ProcessBuilder("systemctl", "reboot").inheritIO().start();
        } catch (IOException e) {
            LOG.warn("Cannot restart", e);
            JOptionPane.showMessageDialog(this, msg.format("restart.failed", e.getMessage()), getTitle(),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    boolean restartBarShown() {
        return restartBar.isVisible();
    }

    UpdatesTab updatesTab() {
        return updatesTab;
    }

    CatalogTab catalogTab(Category category) {
        return catalogTabs.get(category);
    }

    OperationBar operation() {
        return operation;
    }

    JTextField searchField() {
        return search;
    }

    JCheckBox librariesSwitch() {
        return libraries;
    }

    JComboBox<String> showFilter() {
        return show;
    }

    /** The titles of the tabs as shown. */
    List<String> tabTitles() {
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) titles.add(tabs.getTitleAt(i));
        return titles;
    }

    void selectTab(java.awt.Component tab) {
        tabs.setSelectedComponent(tab);
    }

    String updatesTabTitle() {
        int index = tabs.indexOfComponent(updatesTab);
        return index < 0 ? null : tabs.getTitleAt(index);
    }

    private static String message(Throwable error) {
        return error.getMessage() != null ? error.getMessage() : error.toString();
    }

    private <T> void background(Callable<T> work, Consumer<T> done, Consumer<Throwable> failed) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return work.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor).whenCompleteAsync((result, error) -> {
            if (error == null) {
                done.accept(result);
            } else {
                failed.accept(error instanceof CompletionException && error.getCause() != null ? error.getCause() : error);
            }
        }, edt);
    }

    private Action action(String key, KeyStroke accelerator, Runnable run) {
        Mnemonics.Text text = Mnemonics.parse(msg.get(key));
        Action action = new AbstractAction(text.text()) {
            @Override
            public void actionPerformed(ActionEvent e) {
                run.run();
            }
        };
        if (text.index() >= 0) {
            action.putValue(Action.MNEMONIC_KEY, text.keyCode());
            action.putValue(Action.DISPLAYED_MNEMONIC_INDEX_KEY, text.index());
        }
        if (accelerator != null) action.putValue(Action.ACCELERATOR_KEY, accelerator);
        return action;
    }
}
