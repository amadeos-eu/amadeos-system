package eu.amadeos.system.software.ui;

import com.google.inject.Inject;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.system.packages.Change;
import eu.amadeos.system.packages.PackageInfo;
import eu.amadeos.system.packages.PackageService;
import eu.amadeos.system.packages.Progress;
import eu.amadeos.system.packages.ProgressListener;
import eu.amadeos.system.packages.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The Software window. One program for everything about installed software: the Updates tab now, the catalog
 * tabs (Applications, Command line, Libraries) next. apt is asked on the background executor through
 * {@link PackageService}; one operation runs at a time and shows its progress in the bar at the bottom.
 */
public final class MainWindow extends JFrame {
    private static final Logger LOG = LoggerFactory.getLogger(MainWindow.class);
    static final Color RESTART_BACKGROUND = new Color(0xF4, 0xE2, 0x8C);

    private final PackageService packages;
    private final Messages msg;
    private final ExecutorService executor;
    private final EdtExecutor edt;

    private final JTabbedPane tabs = new JTabbedPane();
    private final UpdatesTab updatesTab;
    private final OperationBar operation;
    private final JPanel restartBar = new JPanel(new BorderLayout(8, 0));
    private final Action check;
    private final Action settings;
    private boolean busy;

    @Inject
    public MainWindow(PackageService packages, Messages msg, ExecutorService executor, EdtExecutor edt) {
        super(msg.get("app.title"));
        this.packages = packages;
        this.msg = msg;
        this.executor = executor;
        this.edt = edt;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        updatesTab = new UpdatesTab(msg, this::loadDetails, this::installUpdates, this::installSecurityUpdates);
        operation = new OperationBar(msg);

        check = action("action.check", KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), this::checkNow);
        settings = action("action.settings", null, this::openSettings);
        Action exit = action("action.exit", KeyStroke.getKeyStroke(KeyEvent.VK_Q, KeyEvent.CTRL_DOWN_MASK),
                this::dispose);
        JMenuBar bar = new JMenuBar();
        JMenu software = Mnemonics.apply(new JMenu(), msg.get("menu.software"));
        software.add(check);
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

        tabs.addTab(msg.get("tab.updates"), updatesTab);
        add(restartBar, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        add(operation, BorderLayout.SOUTH);

        setSize(new Dimension(980, 620));
        setLocationRelativeTo(null);
    }

    /** Reads the waiting updates; with {@code openSettings} the settings dialog opens as well. */
    public void start(boolean openSettings) {
        reload();
        if (openSettings) openSettings();
    }

    /** Reads the waiting updates and the time of the last check again. */
    void reload() {
        background(() -> new Snapshot(packages.upgrades(), packages.lastRefresh()), this::showUpdates,
                error -> operation.end(msg.format("error.read", message(error))));
    }

    record Snapshot(List<Change> updates, Optional<Instant> lastRefresh) {
    }

    void showUpdates(Snapshot snapshot) {
        updatesTab.setUpdates(snapshot.updates(), snapshot.lastRefresh());
        int count = snapshot.updates().size();
        tabs.setTitleAt(tabs.indexOfComponent(updatesTab),
                count == 0 ? msg.get("tab.updates") : msg.format("tab.updates.count", count));
    }

    private void loadDetails(Change update) {
        background(() -> packages.details(update.name()), info -> updatesTab.showDetails(update, info),
                error -> LOG.debug("No details for {}", update.name(), error));
    }

    private void installUpdates(List<String> names) {
        perform(msg.get("progress.installing"), listener -> packages.upgrade(names, false, listener), true);
    }

    private void installSecurityUpdates() {
        perform(msg.get("progress.installing"), listener -> packages.upgrade(List.of(), true, listener), true);
    }

    private void checkNow() {
        perform(msg.get("progress.checking"), packages::refresh, false);
    }

    private void openSettings() {
        background(packages::automaticSecurityUpdates, automatic -> new SettingsDialog(this, msg, automatic,
                on -> perform(msg.get("progress.settings"),
                        listener -> packages.setAutomaticSecurityUpdates(on, listener), false)).setVisible(true),
                error -> operation.end(msg.format("error.read", message(error))));
    }

    /**
     * Runs one change through the helper: progress and apt's messages in the bar, the outcome in its status
     * line (and in a dialog when it failed), the updates read again afterwards.
     */
    void perform(String title, Function<ProgressListener, Result> work, boolean installing) {
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
        background(() -> work.apply(listener), result -> finished(result, installing),
                error -> finished(new Result(Result.Status.FAILED, message(error), false), installing));
    }

    private void finished(Result result, boolean installing) {
        setBusy(false);
        operation.end(outcome(msg, result, installing));
        if (result.rebootRequired()) restartBar.setVisible(true);
        if (result.status() == Result.Status.FAILED) {
            operation.showLog(true);
            JOptionPane.showMessageDialog(this, outcome(msg, result, installing), getTitle(),
                    JOptionPane.ERROR_MESSAGE);
        }
        reload();
    }

    /** The status line after an operation. */
    static String outcome(Messages msg, Result result, boolean installing) {
        return switch (result.status()) {
            case DONE -> msg.get(installing ? "outcome.installed" : "outcome.done");
            case LOCKED -> msg.get("outcome.locked");
            case NOT_AUTHORIZED -> msg.get("outcome.not-authorized");
            case FAILED -> msg.format("outcome.failed", result.message());
        };
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        updatesTab.setBusy(busy);
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

    OperationBar operation() {
        return operation;
    }

    String updatesTabTitle() {
        return tabs.getTitleAt(tabs.indexOfComponent(updatesTab));
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
