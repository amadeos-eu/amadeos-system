package eu.amadeos.system.settings;

import eu.amadeos.system.base.xdg.MimeApps;
import eu.amadeos.system.base.xdg.XdgDesktopEntry;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Default Applications: which program opens web links, mail addresses, folders, text, documents, pictures, PDF
 * files, e-books, music, videos, calendars and contacts, for {@code xdg-open} and so for Files, Mail, the
 * browser and every other program. The choices go to the user's {@code ~/.config/mimeapps.list}
 * ({@link MimeApps}); choosing amadeOS's own default again removes them from there.
 */
final class ApplicationsPage extends SettingsPage {
    /** NO program: the item of a list whose type has no default and whose programs are none of the offered. */
    static final String NONE = "";

    // --8<-- [start:roles]
    /** The kinds of things a default program is chosen for: the first type decides which programs are offered. */
    enum Role {
        WEB("x-scheme-handler/http", "x-scheme-handler/https", "text/html", "application/xhtml+xml"),
        MAIL("x-scheme-handler/mailto"),
        FILES("inode/directory"),
        TEXT("text/plain"),
        DOCUMENTS("application/vnd.oasis.opendocument.text",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/msword",
                "application/rtf", "text/rtf"),
        PICTURES("image/jpeg", "image/png", "image/gif", "image/bmp", "image/x-bmp", "image/tiff", "image/webp"),
        PDF("application/pdf"),
        EBOOKS("application/epub+zip", "application/vnd.comicbook+zip", "application/x-cbz"),
        MUSIC("audio/mpeg", "audio/ogg", "audio/x-vorbis+ogg", "audio/flac", "audio/x-flac", "audio/wav",
                "audio/x-wav", "audio/aac", "audio/x-mpegurl"),
        VIDEOS("video/mp4", "video/x-matroska", "video/webm", "video/x-msvideo", "video/quicktime", "video/mpeg",
                "video/ogg", "video/x-m4v"),
        CALENDAR("text/calendar"),
        CONTACTS("text/vcard", "text/x-vcard");

        private final List<String> types;

        Role(String... types) {
            this.types = List.of(types);
        }

        /** The type that decides which programs are offered and which one is the default now. */
        String primary() {
            return types.getFirst();
        }

        List<String> types() {
            return types;
        }

        String label() {
            return SettingsTexts.get("applications." + name().toLowerCase(Locale.ROOT));
        }
    }
    // --8<-- [end:roles]

    private final MimeApps mimeApps;
    private final Map<String, XdgDesktopEntry> programs = new java.util.HashMap<>();
    private final Map<Role, JComboBox<String>> choices = new EnumMap<>(Role.class);

    ApplicationsPage(Consumer<String> status, MimeApps mimeApps, List<XdgDesktopEntry> applications) {
        super(SettingsCategory.APPLICATIONS, status);
        this.mimeApps = mimeApps;
        for (XdgDesktopEntry e : applications) programs.putIfAbsent(e.id(), e);

        JPanel table = new JPanel(new GridBagLayout());
        table.setAlignmentX(Component.LEFT_ALIGNMENT);
        int row = 0;
        for (Role role : Role.values()) {
            JComboBox<String> box = new JComboBox<>();
            fill(box, role);
            box.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                              boolean focus) {
                    super.getListCellRendererComponent(list, value, index, selected, focus);
                    setText(programLabel(role, (String) value));
                    return this;
                }
            });
            choices.put(role, box);
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = row++;
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(2, 8, 2, 8);
            c.gridx = 0;
            table.add(new JLabel(role.label()), c);
            c.gridx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            table.add(box, c);
        }
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 2;
        filler.weightx = 1;
        table.add(javax.swing.Box.createHorizontalGlue(), filler);

        JButton defaults = new JButton(SettingsTexts.get("applications.defaults"));
        defaults.addActionListener(e -> useSystemDefaults());
        layoutSections(
                section(SettingsTexts.get("applications.title"), table, note(SettingsTexts.get("applications.hint"))),
                line(defaults),
                note(SettingsTexts.get("applications.more")));
    }

    /** The programs that open the role's first type, the current default selected; "none" if there is none. */
    private void fill(JComboBox<String> box, Role role) {
        box.removeAllItems();
        List<String> offered = candidates(role);
        Optional<String> current = mimeApps.defaultFor(role.primary());
        if (current.isPresent() && !offered.contains(current.get())) offered.addFirst(current.get());
        if (current.isEmpty() || offered.isEmpty()) offered.addFirst(NONE);
        for (String id : offered) box.addItem(id);
        box.setSelectedItem(current.orElse(NONE));
        box.setEnabled(offered.size() > 1 || !offered.contains(NONE));
    }

    /** Installed programs that say they open the role's first type, by name. */
    List<String> candidates(Role role) {
        List<String> ids = new ArrayList<>(programs.values().stream()
                .filter(p -> p.mimeTypes().contains(role.primary()))
                .sorted(Comparator.comparing(XdgDesktopEntry::name, String.CASE_INSENSITIVE_ORDER))
                .map(XdgDesktopEntry::id).toList());
        return ids;
    }

    private String programLabel(Role role, String id) {
        if (id == null) return "";
        if (id.equals(NONE)) {
            return SettingsTexts.get(candidates(role).isEmpty() ? "applications.none.installed" : "applications.none");
        }
        String name = Optional.ofNullable(programs.get(id)).map(XdgDesktopEntry::name)
                .orElse(id.endsWith(".desktop") ? id.substring(0, id.length() - 8) : id);
        return mimeApps.systemDefaultFor(role.primary()).filter(id::equals).isPresent()
                ? SettingsTexts.format("applications.system.default", name) : name;
    }

    JComboBox<String> choice(Role role) {
        return choices.get(role);
    }

    /** Selects amadeOS's own default in every list; Apply then removes the user's choices. */
    void useSystemDefaults() {
        for (Role role : Role.values()) {
            mimeApps.systemDefaultFor(role.primary()).ifPresent(id -> {
                JComboBox<String> box = choices.get(role);
                if (((javax.swing.DefaultComboBoxModel<String>) box.getModel()).getIndexOf(id) >= 0) box.setSelectedItem(id);
            });
        }
    }

    // --8<-- [start:apply]
    @Override
    protected String apply() {
        int changed = 0;
        try {
            for (Role role : Role.values()) {
                String chosen = (String) choices.get(role).getSelectedItem();
                if (chosen == null || chosen.equals(NONE)) continue;
                Optional<String> system = mimeApps.systemDefaultFor(role.primary());
                if (system.filter(chosen::equals).isPresent()) {
                    // amadeOS's own choice again: drop the user's lines, so that later image updates apply.
                    if (role.types().stream().anyMatch(mimeApps::userChoice)) {
                        mimeApps.reset(role.types());
                        changed++;
                    }
                } else if (!mimeApps.defaultFor(role.primary()).filter(chosen::equals).isPresent()) {
                    mimeApps.setDefault(chosen, typesOf(role, chosen));
                    changed++;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        return changed == 0 ? SettingsTexts.get("applications.status.unchanged")
                : SettingsTexts.format("applications.status", changed);
    }
    // --8<-- [end:apply]

    /** The role's types the program says it opens, always with the first one. */
    private List<String> typesOf(Role role, String id) {
        Set<String> types = new LinkedHashSet<>();
        types.add(role.primary());
        XdgDesktopEntry program = programs.get(id);
        for (String type : role.types()) {
            if (program == null || program.mimeTypes().contains(type)) types.add(type);
        }
        return List.copyOf(types);
    }

    @Override
    protected void cancelled() {
        for (Role role : Role.values()) fill(choices.get(role), role);
    }
}
