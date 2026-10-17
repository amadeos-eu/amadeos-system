package org.datazuul.amadeos.wm.panel;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import org.datazuul.amadeos.commons.theme.DesktopTheme;
import org.datazuul.amadeos.base.theme.ThemeManager;

/**
 * Session Diagnostics &amp; Logs in the Tools drawer: one log at a time, chosen from a list (see
 * {@link DesktopLogs}), shown from its end; <i>Follow</i> reloads it every two seconds.
 */
public final class LogViewerDialog extends JDialog {
    private static final int FOLLOW_MILLIS = 2000;

    private final JComboBox<DesktopLogs.Source> logs = new JComboBox<>();
    private final JTextArea text = new JTextArea();
    private final JCheckBox follow = new JCheckBox("Follow");
    private final Timer followTimer = new Timer(FOLLOW_MILLIS, e -> reload());
    private boolean loading;

    /** @param openFolder opens the log directory in the file manager; null leaves the button out */
    public LogViewerDialog(JFrame parent, Runnable openFolder) {
        super(parent, "amadeOS Session Diagnostics & Logs", false);
        DesktopTheme t = ThemeManager.currentTheme();
        setLayout(new BorderLayout(8, 8));
        getContentPane().setBackground(t.base());

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setBackground(t.base());
        top.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 12));
        JLabel label = new JLabel("Log:");
        label.setForeground(t.textActive());
        label.setLabelFor(logs);
        label.setDisplayedMnemonic(KeyEvent.VK_L);
        top.add(label, BorderLayout.WEST);
        logs.setMaximumRowCount(20);
        logs.addActionListener(e -> reload());
        top.add(logs, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        text.setEditable(false);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(text);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 12, 0, 12), scroll.getBorder()));
        add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        bottom.setBackground(t.base());
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 12, 10, 12));
        follow.setOpaque(false);
        follow.setForeground(t.textActive());
        follow.setMnemonic(KeyEvent.VK_F);
        follow.setToolTipText("Reload the log every two seconds and keep its end in view");
        follow.addActionListener(e -> {
            if (follow.isSelected()) {
                followTimer.start();
                reload();
            } else {
                followTimer.stop();
            }
        });
        bottom.add(follow);
        JButton reloadButton = new JButton("Reload");
        reloadButton.setMnemonic(KeyEvent.VK_R);
        reloadButton.addActionListener(e -> refreshList());
        bottom.add(reloadButton);
        if (openFolder != null) {
            JButton folder = new JButton("Open Folder");
            folder.setMnemonic(KeyEvent.VK_O);
            folder.setToolTipText("The log directory in the file manager");
            folder.addActionListener(e -> openFolder.run());
            bottom.add(folder);
        }
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        bottom.add(close);
        add(bottom, BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e -> refreshList(), KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        followTimer.setRepeats(true);

        refreshList();
        setPreferredSize(new Dimension(760, 480));
        pack();
        setLocationRelativeTo(parent);
    }

    /** Lists the logs again (an application started since may have a new one) and keeps the selection. */
    private void refreshList() {
        DesktopLogs.Source selected = (DesktopLogs.Source) logs.getSelectedItem();
        List<DesktopLogs.Source> available = DesktopLogs.available();
        loading = true;
        try {
            logs.removeAllItems();
            for (DesktopLogs.Source source : available) {
                logs.addItem(source);
            }
            if (selected != null) {
                for (DesktopLogs.Source source : available) {
                    if (source.label().equals(selected.label())) {
                        logs.setSelectedItem(source);
                    }
                }
            }
        } finally {
            loading = false;
        }
        reload();
    }

    /** Reads the chosen log off the event thread and shows its end. */
    private void reload() {
        if (loading) {
            return;
        }
        DesktopLogs.Source source = (DesktopLogs.Source) logs.getSelectedItem();
        if (source == null) {
            text.setText("(No logs yet: the session writes its log to " + "~/.cache/amadeos/logs.)");
            return;
        }
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return source.read();
            }

            @Override
            protected void done() {
                if (logs.getSelectedItem() != source) {
                    return;
                }
                try {
                    text.setText(get());
                } catch (Exception e) {
                    text.setText("(Cannot read the log: " + e.getMessage() + ")");
                }
                text.setCaretPosition(text.getDocument().getLength());
            }
        }.execute();
    }

    @Override
    public void dispose() {
        followTimer.stop();
        super.dispose();
    }
}
