package org.datazuul.dazeos.wm.panel.widget;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.Timer;
import org.datazuul.dazeos.wm.theme.DesktopTheme;

public final class MemoryDockWidget extends AbstractDockWidget {
    private final JPanel panel;
    private final JLabel valueLabel;
    private final JProgressBar progressBar;
    private final Timer timer;

    public MemoryDockWidget() {
        super("memory");
        panel = new JPanel(new GridLayout(2, 1, 0, 1));
        panel.setPreferredSize(new Dimension(54, 48));
        panel.setBorder(new MotifBevelBorder(false, 2));

        valueLabel = new JLabel("0M", JLabel.CENTER);
        valueLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 9));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(false);
        progressBar.setPreferredSize(new Dimension(46, 7));
        progressBar.setBorder(BorderFactory.createEmptyBorder(2, 4, 3, 4));

        panel.add(valueLabel);
        panel.add(progressBar);

        updateThemeStyles(currentTheme);
        updateMemory();

        timer = new Timer(2000, e -> updateMemory());
    }

    private void updateMemory() {
        long total = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        long free = Runtime.getRuntime().freeMemory() / (1024 * 1024);
        long used = total - free;
        int pct = (total > 0) ? (int) ((used * 100) / total) : 0;

        valueLabel.setText(used + "M");
        progressBar.setValue(pct);
        panel.setToolTipText("JVM Memory: " + used + " MB / " + total + " MB (" + pct + "%)");
    }

    @Override
    public JComponent component() {
        return panel;
    }

    @Override
    public void start() {
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    @Override
    public void stop() {
        timer.stop();
    }

    @Override
    protected void updateThemeStyles(DesktopTheme theme) {
        panel.setBackground(theme.sunkenBg());
        valueLabel.setForeground(theme.textActive());
        progressBar.setBackground(theme.darkShadow());
        progressBar.setForeground(new Color(0x33, 0xAA, 0x77)); // Activity green
    }
}

