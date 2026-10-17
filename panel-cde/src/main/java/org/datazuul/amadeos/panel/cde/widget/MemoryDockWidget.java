package org.datazuul.amadeos.panel.cde.widget;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.Timer;
import org.datazuul.amadeos.commons.theme.DesktopTheme;

/**
 * Authentic CDE Workstation Front Panel Memory Monitor module:
 * - Top: Drawer notch tab with darker gray background (no triangle arrow), matching Clock.
 * - Divider: Clear horizontal groove dividing top notch from launcher area.
 * - Tab Divider: 2px groove only in top tab area.
 * - Center: Seamless base background with JVM memory label and activity gauge.
 */
public final class MemoryDockWidget extends AbstractDockWidget {

    private final JPanel panel;
    private final JLabel valueLabel;
    private final JProgressBar progressBar;
    private final Timer timer;

    public MemoryDockWidget() {
        super("memory");

        panel = new JPanel(new BorderLayout(0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // Raised drawer tab without triangle + seamless button plate
                CdePanelChrome.paintSlotBay(g, getWidth(), getHeight(), currentTheme);
            }
        };
        panel.setPreferredSize(new Dimension(CdePanelChrome.SLOT_WIDTH, CdePanelChrome.SLOT_HEIGHT));
        panel.setBackground(currentTheme.base());

        // Spacer covering the drawer tab row
        JPanel notchSpacer = new JPanel();
        notchSpacer.setOpaque(false);
        notchSpacer.setPreferredSize(new Dimension(CdePanelChrome.SLOT_WIDTH, CdePanelChrome.TAB_HEIGHT + 1));
        panel.add(notchSpacer, BorderLayout.NORTH);

        // Meter content panel in button area below notch
        JPanel meterPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        meterPanel.setOpaque(false);
        meterPanel.setBorder(BorderFactory.createEmptyBorder(10, 4, 10, 4));

        valueLabel = new JLabel("0M", JLabel.CENTER);
        valueLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(false);
        progressBar.setPreferredSize(new Dimension(44, 9));
        progressBar.setBorder(new MotifBevelBorder(false, 1));

        meterPanel.add(valueLabel);
        meterPanel.add(progressBar);
        panel.add(meterPanel, BorderLayout.CENTER);

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
        panel.setBackground(theme.base());
        valueLabel.setForeground(theme.textActive());
        progressBar.setBackground(theme.sunkenBg());
        progressBar.setForeground(new Color(0x33, 0xAA, 0x77)); // Activity green
        panel.repaint();
    }
}
