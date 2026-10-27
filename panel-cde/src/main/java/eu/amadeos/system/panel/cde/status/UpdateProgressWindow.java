package eu.amadeos.system.panel.cde.status;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.WindowConstants;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.packages.Progress;

/**
 * The window in which the updates cell shows an installation: what apt does just now, a progress bar and, behind
 * "Details", apt's and dpkg's messages. Hiding it leaves the installation running (the cell keeps its progress
 * bar and can show the window again); at the end it says how the installation went.
 */
final class UpdateProgressWindow implements UpdatesCell.ProgressView {
    private static final int MAX_LOG_CHARS = 200_000;

    private final Messages texts;
    private final JFrame frame;
    private final JLabel status = new JLabel(" ");
    private final JProgressBar bar = new JProgressBar(0, 100);
    private final JToggleButton details;
    private final JTextArea log = new JTextArea(12, 60);
    private final JScrollPane logScroll = new JScrollPane(log);
    private final JButton close;
    private boolean running;

    /** A window, or a view that shows nothing where there is no screen (tests, a headless session). */
    static UpdatesCell.ProgressView create(Messages texts, DesktopTheme theme) {
        return GraphicsEnvironment.isHeadless() ? UpdatesCell.ProgressView.NONE : new UpdateProgressWindow(texts, theme);
    }

    private UpdateProgressWindow(Messages texts, DesktopTheme theme) {
        this.texts = texts;
        frame = new JFrame(texts.get("updates.title"));
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

        details = new JToggleButton(texts.get("updates.progress.details"));
        details.addActionListener(e -> showLog(details.isSelected()));
        close = new JButton(texts.get("updates.progress.hide"));
        close.addActionListener(e -> frame.setVisible(false));
        bar.setStringPainted(true);
        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, log.getFont().getSize()));
        logScroll.setVisible(false);
        status.setPreferredSize(new Dimension(420, status.getPreferredSize().height));

        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setOpaque(false);
        top.add(status, BorderLayout.NORTH);
        top.add(bar, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(details);
        buttons.add(close);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 14, 10, 14));
        content.setBackground(theme.base());
        status.setForeground(theme.textActive());
        content.add(top, BorderLayout.NORTH);
        content.add(logScroll, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        frame.setContentPane(content);
        frame.pack();
    }

    @Override
    public void begin() {
        running = true;
        status.setText(texts.get("updates.progress.start"));
        bar.setIndeterminate(true);
        bar.setString("");
        bar.setVisible(true);
        log.setText("");
        close.setText(texts.get("updates.progress.hide"));
        show();
    }

    @Override
    public void progress(Progress progress) {
        bar.setIndeterminate(false);
        bar.setValue(progress.overall());
        bar.setString(progress.overall() + " %");
        if (!progress.message().isBlank()) status.setText(progress.message());
    }

    @Override
    public void message(String line) {
        log.append(line + "\n");
        // A long upgrade writes a lot; the oldest half goes when the log grows too big.
        int length = log.getDocument().getLength();
        if (length > MAX_LOG_CHARS) log.replaceRange("", 0, length / 2);
        log.setCaretPosition(log.getDocument().getLength());
    }

    @Override
    public boolean finish(String message, boolean warning) {
        running = false;
        if (message == null || !frame.isVisible()) {
            frame.setVisible(false);
            return false;
        }
        bar.setVisible(false);
        status.setText("<html>" + escape(message).replace("\n", "<br>") + "</html>");
        // A failure is easier to understand with apt's own words in view.
        if (warning) showLog(true);
        close.setText(texts.get("updates.progress.close"));
        frame.pack();
        frame.toFront();
        return true;
    }

    @Override
    public boolean running() {
        return running;
    }

    @Override
    public void show() {
        if (!frame.isVisible()) {
            Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            frame.setLocation(screen.x + (screen.width - frame.getWidth()) / 2,
                    screen.y + (screen.height - frame.getHeight()) / 3);
            frame.setVisible(true);
        }
        frame.toFront();
    }

    private void showLog(boolean show) {
        details.setSelected(show);
        logScroll.setVisible(show);
        frame.pack();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
