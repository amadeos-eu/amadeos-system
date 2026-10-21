package eu.amadeos.system.software.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.packages.CatalogEntry;
import eu.amadeos.system.packages.PackageInfo;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The right side of a catalog tab: what the chosen package is, whether and how it is installed, and Install,
 * Remove or Update.
 */
final class CatalogDetails extends JPanel {
    private final Messages msg;
    private final JLabel title = new JLabel(" ");
    private final JLabel summary = new JLabel(" ");
    private final JLabel state = new JLabel(" ");
    private final JLabel size = new JLabel(" ");
    private final JLabel homepage = new JLabel(" ");
    private final JTextArea description = new JTextArea();
    private final JButton install;
    private final JButton remove;
    private final JButton update;
    private CatalogEntry entry;
    private Optional<PackageInfo> info = Optional.empty();
    private boolean busy;

    CatalogDetails(Messages msg, Consumer<CatalogEntry> onInstall, Consumer<CatalogEntry> onRemove,
                   Consumer<CatalogEntry> onUpdate) {
        super(new BorderLayout(0, 8));
        this.msg = msg;
        setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 2));
        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        for (JLabel label : new JLabel[]{title, summary, state, size, homepage}) {
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            head.add(label);
        }
        description.setEditable(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setOpaque(false);
        JScrollPane scroll = new JScrollPane(description);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        install = new JButton(msg.get("action.install"));
        install.addActionListener(e -> onInstall.accept(entry));
        remove = new JButton(msg.get("action.remove"));
        remove.addActionListener(e -> onRemove.accept(entry));
        update = new JButton(msg.get("action.update"));
        update.addActionListener(e -> onUpdate.accept(entry));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(remove);
        buttons.add(update);
        buttons.add(install);

        add(head, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        clear();
    }

    void clear() {
        entry = null;
        info = Optional.empty();
        title.setText(msg.get("details.choose"));
        for (JLabel label : new JLabel[]{summary, state, size, homepage}) label.setText(" ");
        description.setText("");
        updateButtons();
    }

    /** The entry at once; the repository's description when it has arrived. */
    void show(CatalogEntry shown, Optional<PackageInfo> info) {
        if (entry == null || !entry.name().equals(shown.name())) this.info = Optional.empty();
        if (info.isPresent()) this.info = info;
        info = this.info;
        entry = shown;
        title.setText(shown.name());
        summary.setText(shown.summary().isBlank() ? " " : shown.summary());
        state.setText(stateText(msg, shown));
        size.setText(info.map(i -> msg.format("details.size", Sizes.bytes(i.downloadSize()),
                Sizes.kib(i.installedSize()))).orElse(" "));
        homepage.setText(info.map(PackageInfo::homepage).filter(s -> !s.isBlank()).orElse(" "));
        description.setText(info.map(PackageInfo::description).orElse(""));
        description.setCaretPosition(0);
        updateButtons();
    }

    /** "Installed: version 1.2; version 1.3 is available." and the like. */
    static String stateText(Messages msg, CatalogEntry e) {
        String text;
        if (!e.installed()) {
            text = msg.format("details.not-installed", e.availableVersion());
        } else if (e.availableVersion().isEmpty()) {
            text = msg.format("details.local", e.installedVersion());
        } else if (e.upgradable()) {
            text = msg.format("details.installed.update", e.installedVersion(), e.availableVersion());
        } else {
            text = msg.format("details.installed", e.installedVersion());
        }
        return e.installed() && e.automatic() ? text + " " + msg.get("details.dependency") : text;
    }

    void setBusy(boolean busy) {
        this.busy = busy;
        updateButtons();
    }

    private void updateButtons() {
        install.setEnabled(!busy && entry != null && !entry.installed() && !entry.availableVersion().isEmpty());
        remove.setEnabled(!busy && entry != null && entry.installed());
        update.setEnabled(!busy && entry != null && entry.upgradable());
        update.setVisible(entry != null && entry.upgradable());
    }

    CatalogEntry entry() {
        return entry;
    }

    Optional<PackageInfo> info() {
        return info;
    }

    String titleText() {
        return title.getText();
    }

    String stateText() {
        return state.getText();
    }

    JButton installButton() {
        return install;
    }

    JButton removeButton() {
        return remove;
    }

    JButton updateButton() {
        return update;
    }
}
