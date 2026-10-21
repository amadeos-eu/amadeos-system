package eu.amadeos.system.software.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.packages.Change;
import eu.amadeos.system.packages.PackageInfo;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.util.Optional;

/**
 * The right side of a tab: what the chosen package is (summary and description from the repository), the
 * versions and where the new one comes from.
 */
final class DetailsPane extends JPanel {
    private final Messages msg;
    private final JLabel title = new JLabel(" ");
    private final JLabel summary = new JLabel(" ");
    private final JLabel versions = new JLabel(" ");
    private final JLabel source = new JLabel(" ");
    private final JLabel homepage = new JLabel(" ");
    private final JTextArea description = new JTextArea();

    DetailsPane(Messages msg) {
        super(new BorderLayout(0, 8));
        this.msg = msg;
        setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 2));
        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        for (JLabel label : new JLabel[]{title, summary, versions, source, homepage}) {
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
        add(head, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        clear();
    }

    void clear() {
        title.setText(msg.get("details.none"));
        for (JLabel label : new JLabel[]{summary, versions, source, homepage}) label.setText(" ");
        description.setText("");
    }

    /** The update at once; the repository's description when it has arrived. */
    void show(Change update, Optional<PackageInfo> info) {
        title.setText(update.name());
        versions.setText(msg.format("details.versions", update.installedVersion(), update.newVersion()));
        source.setText(update.security() ? msg.format("details.security", update.originLabel())
                : msg.format("details.source", update.originLabel()));
        summary.setText(info.map(PackageInfo::summary).filter(s -> !s.isBlank()).orElse(" "));
        homepage.setText(info.map(PackageInfo::homepage).filter(s -> !s.isBlank()).orElse(" "));
        description.setText(info.map(PackageInfo::description).orElse(""));
        description.setCaretPosition(0);
    }

    String titleText() {
        return title.getText();
    }

    String sourceText() {
        return source.getText();
    }

    String summaryText() {
        return summary.getText();
    }
}
