package eu.amadeos.system.software.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.packages.Progress;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;

/**
 * The bottom of the window: what is going on (a progress bar while apt works, the outcome afterwards) and,
 * behind "Details", apt's messages of the last operation.
 */
final class OperationBar extends JPanel {
    private static final int MAX_LOG_CHARS = 200_000;

    private final JLabel status = new JLabel(" ");
    private final JProgressBar bar = new JProgressBar(0, 100);
    private final JToggleButton details;
    private final JTextArea log = new JTextArea();
    private final JScrollPane logScroll = new JScrollPane(log);

    OperationBar(Messages msg) {
        super(new BorderLayout(6, 4));
        setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 8));
        details = new JToggleButton(msg.get("action.details"));
        details.addActionListener(e -> showLog(details.isSelected()));
        bar.setStringPainted(true);
        bar.setVisible(false);
        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, log.getFont().getSize()));
        logScroll.setPreferredSize(new Dimension(200, 160));
        logScroll.setVisible(false);

        JPanel line = new JPanel(new BorderLayout(8, 0));
        line.setOpaque(false);
        line.add(status, BorderLayout.CENTER);
        line.add(bar, BorderLayout.EAST);
        bar.setPreferredSize(new Dimension(220, bar.getPreferredSize().height));
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.add(line, BorderLayout.CENTER);
        row.add(details, BorderLayout.EAST);
        add(logScroll, BorderLayout.CENTER);
        add(row, BorderLayout.SOUTH);
    }

    void begin(String text) {
        status.setText(text);
        bar.setIndeterminate(true);
        bar.setValue(0);
        bar.setString("");
        bar.setVisible(true);
        log.setText("");
    }

    void progress(Progress progress) {
        bar.setIndeterminate(false);
        bar.setValue(progress.overall());
        bar.setString(progress.overall() + " %");
        status.setText(progress.message());
    }

    void message(String line) {
        log.append(line + "\n");
        // A long upgrade writes a lot; the oldest half goes when the log grows too big.
        int length = log.getDocument().getLength();
        if (length > MAX_LOG_CHARS) log.replaceRange("", 0, length / 2);
        log.setCaretPosition(log.getDocument().getLength());
    }

    void end(String text) {
        bar.setVisible(false);
        status.setText(text);
    }

    void showLog(boolean show) {
        details.setSelected(show);
        logScroll.setVisible(show);
        revalidate();
    }

    String statusText() {
        return status.getText();
    }

    String logText() {
        return log.getText();
    }

    boolean busy() {
        return bar.isVisible();
    }
}
