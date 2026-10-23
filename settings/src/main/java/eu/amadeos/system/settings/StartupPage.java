package eu.amadeos.system.settings;

import eu.amadeos.system.base.startup.DesktopServices;
import eu.amadeos.system.base.startup.StartupSettings;
import eu.amadeos.system.base.startup.UserAutostart;
import eu.amadeos.system.base.xdg.XdgDesktopEntry;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import java.awt.Component;
import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Startup: the user's programs started at every login ({@code ~/.config/autostart}) and the desktop's own
 * services ({@code /etc/amadeos/autostart.d}), of which the optional ones can be switched off
 * ({@code startup.conf}). Both take effect at the next login.
 */
final class StartupPage extends SettingsPage {
    /** Services the desktop cannot do without: input settings, password prompts of polkit, the screen lock. */
    static final Set<String> REQUIRED = Set.of("10-amadeos-input", "30-amadeos-polkit-agent", "50-amadeos-screen");

    private final StartupSettings settings;
    private final UserAutostart autostart;
    private final Supplier<List<XdgDesktopEntry>> applications;
    private final DefaultListModel<XdgDesktopEntry> programs = new DefaultListModel<>();
    private final JList<XdgDesktopEntry> programList = new JList<>(programs);
    private final Map<String, JCheckBox> services = new LinkedHashMap<>();

    StartupPage(Consumer<String> status, StartupSettings settings, UserAutostart autostart, Path servicesDir,
                Supplier<List<XdgDesktopEntry>> applications) {
        super(SettingsCategory.STARTUP, status);
        this.settings = settings;
        this.autostart = autostart;
        this.applications = applications;

        autostart.entries().forEach(programs::addElement);
        programList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        programList.setVisibleRowCount(5);
        programList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                return super.getListCellRendererComponent(list, ((XdgDesktopEntry) value).name(), index, selected, focus);
            }
        });
        JScrollPane scroll = new JScrollPane(programList);
        scroll.setPreferredSize(new Dimension(320, 110));
        JButton add = new JButton(SettingsTexts.get("startup.add"));
        JButton remove = new JButton(SettingsTexts.get("startup.remove"));
        add.addActionListener(e -> chooseProgram());
        remove.addActionListener(e -> {
            int index = programList.getSelectedIndex();
            if (index >= 0) programs.remove(index);
        });
        remove.setEnabled(false);
        programList.addListSelectionListener(e -> remove.setEnabled(programList.getSelectedIndex() >= 0));

        Set<String> disabled = settings.disabled();
        List<JComponent> serviceLines = new ArrayList<>();
        for (String name : DesktopServices.in(servicesDir)) {
            JCheckBox box = new JCheckBox(serviceLabel(name), !disabled.contains(name) || REQUIRED.contains(name));
            if (REQUIRED.contains(name)) {
                box.setEnabled(false);
                box.setToolTipText(SettingsTexts.get("startup.service.required"));
            }
            services.put(name, box);
            serviceLines.add(box);
        }
        if (serviceLines.isEmpty()) serviceLines.add(note(SettingsTexts.get("startup.services.none")));
        serviceLines.add(note(SettingsTexts.get("startup.services.hint")));

        layoutSections(
                section(SettingsTexts.get("startup.programs.title"), line(scroll), line(add, remove),
                        note(SettingsTexts.get("startup.programs.hint"))),
                section(SettingsTexts.get("startup.services.title"), serviceLines.toArray(JComponent[]::new)),
                note(SettingsTexts.get("startup.session.planned")));
    }

    /** A service's name in the language of the Settings, from {@code startup.service.<script>}, else the file name. */
    static String serviceLabel(String script) {
        String key = "startup.service." + script;
        return SettingsTexts.texts().has(key) ? SettingsTexts.get(key) : script;
    }

    private void chooseProgram() {
        List<XdgDesktopEntry> choices = applications.get().stream()
                .filter(e -> e.isVisible() && e.isInstalled())
                .filter(e -> !contains(e))
                .sorted(Comparator.comparing(XdgDesktopEntry::name, String.CASE_INSENSITIVE_ORDER)).toList();
        if (choices.isEmpty()) {
            status(SettingsTexts.get("startup.add.none"));
            return;
        }
        JList<XdgDesktopEntry> list = new JList<>(choices.toArray(XdgDesktopEntry[]::new));
        list.setCellRenderer(programList.getCellRenderer());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(12);
        int answer = JOptionPane.showConfirmDialog(this, new JScrollPane(list), SettingsTexts.get("startup.add.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer == JOptionPane.OK_OPTION && list.getSelectedValue() != null) {
            programs.addElement(list.getSelectedValue());
        }
    }

    private boolean contains(XdgDesktopEntry entry) {
        for (int i = 0; i < programs.size(); i++) {
            if (programs.get(i).id().equals(entry.id())) return true;
        }
        return false;
    }

    DefaultListModel<XdgDesktopEntry> programs() {
        return programs;
    }

    Map<String, JCheckBox> services() {
        return services;
    }

    @Override
    protected String apply() {
        Set<String> disabled = new TreeSet<>();
        services.forEach((name, box) -> {
            if (!box.isSelected() && !REQUIRED.contains(name)) disabled.add(name);
        });
        // Scripts that are not installed now keep their entry, so a reinstalled package stays switched off.
        for (String name : settings.disabled()) {
            if (!services.containsKey(name)) disabled.add(name);
        }
        settings.saveDisabled(disabled);
        try {
            List<String> wanted = new ArrayList<>();
            for (int i = 0; i < programs.size(); i++) wanted.add(programs.get(i).id());
            for (XdgDesktopEntry existing : autostart.entries()) {
                if (!wanted.contains(existing.id())) autostart.remove(existing);
            }
            List<String> present = autostart.entries().stream().map(XdgDesktopEntry::id).toList();
            for (int i = 0; i < programs.size(); i++) {
                if (!present.contains(programs.get(i).id())) autostart.add(programs.get(i));
            }
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        programs.clear();
        autostart.entries().forEach(programs::addElement);
        return SettingsTexts.get("startup.status");
    }
}
