package eu.amadeos.system.sysinfo.ui;

import com.google.inject.Inject;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.system.sysinfo.domain.Report;
import eu.amadeos.system.sysinfo.service.SystemProbe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/** One page with the sections of the report, and buttons to refresh, copy and save it. */
public final class MainWindow extends JFrame {
    private static final Logger LOG = LoggerFactory.getLogger(MainWindow.class);

    private final SystemProbe probe;
    private final Messages msg;
    private final ExecutorService executor;
    private final EdtExecutor edt;
    private final JPanel page = new Page();
    private final JLabel status = new JLabel(" ");
    private final JButton refresh = new JButton();
    private final JButton copy = new JButton();
    private final JButton save = new JButton();
    private Report report;

    @Inject
    public MainWindow(SystemProbe probe, Messages msg, ExecutorService executor, EdtExecutor edt) {
        super(msg.get("app.title"));
        this.probe = probe;
        this.msg = msg;
        this.executor = executor;
        this.edt = edt;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        page.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
        JScrollPane scroll = new JScrollPane(page, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        Mnemonics.apply(refresh, msg.get("action.refresh")).addActionListener(e -> load());
        Mnemonics.apply(copy, msg.get("action.copy")).addActionListener(e -> copyReport());
        Mnemonics.apply(save, msg.get("action.save")).addActionListener(e -> saveReport());
        JButton close = Mnemonics.apply(new JButton(), msg.get("action.close"));
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(refresh);
        buttons.add(copy);
        buttons.add(save);
        buttons.add(close);
        JPanel bottom = new JPanel(new BorderLayout(12, 0));
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 8));
        bottom.add(status, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
        copy.setEnabled(false);
        save.setEnabled(false);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e -> load(), KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        setSize(new Dimension(680, 620));
        setLocationRelativeTo(null);
    }

    // --8<-- [start:load]
    /** Collects the report in the background and shows it. */
    public void load() {
        EdtExecutor.requireEdt();
        status.setText(msg.get("status.collecting"));
        refresh.setEnabled(false);
        CompletableFuture.supplyAsync(probe::report, executor).whenCompleteAsync((result, error) -> {
            refresh.setEnabled(true);
            if (error != null) {
                LOG.error("Collecting the system information failed", error);
                status.setText(msg.format("error.collect", error.getMessage()));
                return;
            }
            show(result);
            status.setText(" ");
        }, edt);
    }
    // --8<-- [end:load]

    void show(Report result) {
        report = result;
        copy.setEnabled(true);
        save.setEnabled(true);
        page.removeAll();
        // One grid for all sections, so that the labels of every section line up.
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        for (Report.Section section : result.sections()) {
            JLabel title = new JLabel(section.title());
            title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 2f));
            c.gridx = 0;
            c.gridwidth = 2;
            // Only the value column takes extra width; a weight here would widen the label column too.
            c.weightx = 0;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.LINE_START;
            c.insets = new Insets(c.gridy == 0 ? 0 : 14, 0, 2, 0);
            page.add(title, c);
            c.gridy++;
            c.insets = new Insets(0, 0, 4, 0);
            page.add(new JSeparator(), c);
            c.gridy++;
            c.gridwidth = 1;
            for (Report.Item item : section.items()) {
                c.gridx = 0;
                c.weightx = 0;
                c.fill = GridBagConstraints.NONE;
                c.anchor = GridBagConstraints.FIRST_LINE_END;
                c.insets = new Insets(2, 8, 2, 12);
                JLabel label = new JLabel(item.label() + ":", SwingConstants.RIGHT);
                label.setFont(label.getFont().deriveFont(Font.PLAIN));
                page.add(label, c);
                c.gridx = 1;
                c.weightx = 1;
                c.fill = GridBagConstraints.HORIZONTAL;
                c.anchor = GridBagConstraints.FIRST_LINE_START;
                c.insets = new Insets(2, 0, 2, 0);
                JLabel value = new JLabel(item.value());
                // Values wider than the window end in "…"; the tooltip has all of it.
                value.setToolTipText(item.value());
                value.setMinimumSize(new Dimension(0, value.getPreferredSize().height));
                value.setPreferredSize(new Dimension(0, value.getPreferredSize().height));
                page.add(value, c);
                c.gridy++;
            }
        }
        // Takes the height the sections leave, so that they stay at the top of a large window.
        c.gridx = 0;
        c.weightx = 0;
        c.weighty = 1;
        JPanel rest = new JPanel();
        rest.setOpaque(false);
        page.add(rest, c);
        page.revalidate();
        page.repaint();
    }

    private String text() {
        return report.toText(msg.get("report.heading"));
    }

    private void copyReport() {
        if (report == null) return;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text()), null);
        status.setText(msg.get("status.copied"));
    }

    private void saveReport() {
        if (report == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), msg.get("save.default-name")));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (file.exists() && JOptionPane.showConfirmDialog(this, msg.format("save.replace", file.getName()),
                getTitle(), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            Files.writeString(file.toPath(), text(), StandardCharsets.UTF_8);
            status.setText(msg.format("status.saved", file.getName()));
        } catch (IOException e) {
            LOG.warn("Cannot save {}", file, e);
            JOptionPane.showMessageDialog(this, msg.format("error.save", file.getName(), e.getMessage()),
                    getTitle(), JOptionPane.ERROR_MESSAGE);
        }
    }

    /** The page follows the width of the window and scrolls only vertically. */
    private static final class Page extends JPanel implements Scrollable {
        Page() {
            super(new GridBagLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(16, visible.height - 32);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getParent().getHeight() > getPreferredSize().height;
        }
    }
}
