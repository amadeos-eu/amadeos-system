package eu.amadeos.system.settings;

import eu.amadeos.system.packages.Change;
import eu.amadeos.system.packages.PackageService;
import eu.amadeos.system.packages.ProgressListener;
import eu.amadeos.system.packages.Result;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.SwingWorker;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Updates: whether Debian's security updates are installed automatically, and what is waiting now, with the way
 * to Software for the rest. The setting is a file of the system, so Apply goes through the package helper,
 * for which polkit asks for the administrator's password.
 */
final class UpdatesPage extends SettingsPage {
    private final PackageService packages;
    private final Consumer<List<String>> launcher;
    private final JCheckBox automatic = new JCheckBox(SettingsTexts.get("updates.automatic"));
    private final JLabel lastCheck = new JLabel("…");
    private final JLabel waiting = new JLabel("…");
    private Boolean automaticNow;

    /** What the page reads in the background. */
    record Snapshot(Optional<Boolean> automatic, Optional<Instant> lastRefresh, Optional<List<Change>> upgrades) {
    }

    UpdatesPage(Consumer<String> status, PackageService packages, Consumer<List<String>> launcher) {
        super(SettingsCategory.UPDATES, status);
        this.packages = packages;
        this.launcher = launcher;
        automatic.setEnabled(false);
        JButton software = new JButton(SettingsTexts.get("updates.open"));
        software.addActionListener(e -> launcher.accept(List.of("amadeos-software", "--updates")));

        layoutSections(
                section(SettingsTexts.get("updates.automatic.title"), automatic,
                        note(SettingsTexts.get("updates.automatic.hint"))),
                section(SettingsTexts.get("updates.state.title"),
                        line(new JLabel(SettingsTexts.get("updates.last")), lastCheck),
                        line(new JLabel(SettingsTexts.get("updates.waiting")), waiting),
                        line(software)),
                note(SettingsTexts.get("updates.hint")));
        refresh();
    }

    /** Reads the setting and the waiting updates in the background; apt can take a second for the list. */
    void refresh() {
        new SwingWorker<Snapshot, Void>() {
            @Override
            protected Snapshot doInBackground() {
                Optional<Boolean> auto;
                try {
                    auto = Optional.of(packages.automaticSecurityUpdates());
                } catch (Exception e) {
                    auto = Optional.empty();
                }
                Optional<List<Change>> upgrades;
                try {
                    upgrades = Optional.of(packages.upgrades());
                } catch (Exception e) {
                    upgrades = Optional.empty();
                }
                return new Snapshot(auto, packages.lastRefresh(), upgrades);
            }

            @Override
            protected void done() {
                try {
                    show(get());
                } catch (InterruptedException | ExecutionException e) {
                    show(new Snapshot(Optional.empty(), Optional.empty(), Optional.empty()));
                }
            }
        }.execute();
    }

    void show(Snapshot state) {
        automaticNow = state.automatic().orElse(null);
        automatic.setEnabled(automaticNow != null);
        automatic.setSelected(Boolean.TRUE.equals(automaticNow));
        lastCheck.setText(state.lastRefresh()
                .map(t -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                        .withLocale(SettingsTexts.locale()).format(t.atZone(ZoneId.systemDefault())))
                .orElse(SettingsTexts.get("updates.last.never")));
        waiting.setText(state.upgrades().map(UpdatesPage::describe).orElse(SettingsTexts.get("updates.waiting.unknown")));
    }

    static String describe(List<Change> upgrades) {
        if (upgrades.isEmpty()) return SettingsTexts.get("updates.waiting.none");
        long security = upgrades.stream().filter(Change::security).count();
        return security == 0 ? SettingsTexts.format("updates.waiting.count", upgrades.size())
                : SettingsTexts.format("updates.waiting.security", upgrades.size(), security);
    }

    JCheckBox automaticBox() {
        return automatic;
    }

    @Override
    protected String apply() {
        if (automaticNow == null || automatic.isSelected() == automaticNow) {
            return SettingsTexts.get("updates.status.unchanged");
        }
        boolean on = automatic.isSelected();
        new SwingWorker<Result, Void>() {
            @Override
            protected Result doInBackground() {
                return packages.setAutomaticSecurityUpdates(on, ProgressListener.NONE);
            }

            @Override
            protected void done() {
                try {
                    Result result = get();
                    status(result.success() ? SettingsTexts.get(on ? "updates.status.on" : "updates.status.off")
                            : SettingsTexts.format("updates.status.failed", result.message()));
                } catch (InterruptedException | ExecutionException e) {
                    status(SettingsTexts.format("updates.status.failed", e.getMessage()));
                }
                refresh();
            }
        }.execute();
        return SettingsTexts.get("updates.status.saving");
    }

    @Override
    protected void cancelled() {
        if (automaticNow != null) automatic.setSelected(automaticNow);
    }
}
