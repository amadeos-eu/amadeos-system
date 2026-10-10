package org.datazuul.amadeos.wm.panel.widget;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import org.datazuul.amadeos.wm.theme.DesktopTheme;
import org.datazuul.amadeos.wm.theme.IconManager;

public final class WorkspaceSwitcherDockWidget extends AbstractDockWidget {
    private final JPanel pad;
    private final JPanel leftStatus;
    private final JPanel rightStatus;
    private final JPanel grid;
    private final JButton[] workspaceButtons = new JButton[4];
    private final JButton lockBtn;
    private final JButton powerBtn;
    private int activeWorkspace = 1;
    private final Consumer<Integer> workspaceListener;
    private final Runnable powerAction;

    public WorkspaceSwitcherDockWidget(Consumer<Integer> workspaceListener, Runnable powerAction) {
        super("workspace-switcher");
        this.workspaceListener = workspaceListener;
        this.powerAction = powerAction;

        pad = new JPanel(new BorderLayout(3, 3));
        pad.setPreferredSize(new Dimension(220, 52));
        pad.setBorder(new MotifBevelBorder(false, 2));

        // Left Status: Activity LED + Lock Button
        leftStatus = new JPanel(new GridLayout(2, 1, 0, 2));
        leftStatus.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 0));

        JPanel ledPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x10, 0xB9, 0x81)); // Green glow
                g2.fillOval(3, 3, 8, 8);
                g2.setColor(new Color(0x04, 0x78, 0x57));
                g2.drawOval(3, 3, 8, 8);
            }
        };
        ledPanel.setPreferredSize(new Dimension(14, 14));
        ledPanel.setToolTipText("System Activity LED");
        leftStatus.add(ledPanel);

        lockBtn = createIconButton("lock", "Lock Screen");
        lockBtn.addActionListener(e -> JOptionPane.showMessageDialog(pad, "Screen Lock is active.", "Lock", JOptionPane.INFORMATION_MESSAGE));
        leftStatus.add(lockBtn);
        pad.add(leftStatus, BorderLayout.WEST);

        // Center 2x2: Workspace Buttons
        grid = new JPanel(new GridLayout(2, 2, 2, 2));
        String[] wsNames = {"One", "Two", "Three", "Four"};
        for (int i = 0; i < 4; i++) {
            int wsNum = i + 1;
            JButton btn = new JButton(wsNames[i]);
            btn.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
            btn.setFocusPainted(false);
            btn.setMargin(new Insets(1, 4, 1, 4));

            btn.addActionListener(e -> {
                activeWorkspace = wsNum;
                if (this.workspaceListener != null) {
                    this.workspaceListener.accept(wsNum);
                }
                updateAllButtonAppearances();
            });
            workspaceButtons[i] = btn;
            grid.add(btn);
        }
        pad.add(grid, BorderLayout.CENTER);

        // Right Status: Amber Session LED + Power Button
        rightStatus = new JPanel(new GridLayout(2, 1, 0, 2));
        rightStatus.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 2));

        JPanel amberLed = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0xF5, 0x9E, 0x0B)); // Amber
                g2.fillOval(3, 3, 8, 8);
                g2.setColor(new Color(0xB4, 0x53, 0x09));
                g2.drawOval(3, 3, 8, 8);
            }
        };
        amberLed.setPreferredSize(new Dimension(14, 14));
        amberLed.setToolTipText("Session Active");
        rightStatus.add(amberLed);

        powerBtn = createIconButton("power", "Session & Power Controls");
        powerBtn.addActionListener(e -> {
            if (this.powerAction != null) {
                this.powerAction.run();
            }
        });
        rightStatus.add(powerBtn);
        pad.add(rightStatus, BorderLayout.EAST);

        updateThemeStyles(currentTheme);
    }

    private JButton createIconButton(String iconKey, String tooltip) {
        JButton btn = new JButton();
        btn.setIcon(IconManager.getIcon(iconKey, 11));
        btn.setToolTipText(tooltip);
        btn.setPreferredSize(new Dimension(16, 16));
        btn.setMargin(new Insets(1, 1, 1, 1));
        btn.setFocusPainted(false);
        return btn;
    }

    private void updateAllButtonAppearances() {
        for (int i = 0; i < 4; i++) {
            updateButtonAppearance(workspaceButtons[i], (i + 1) == activeWorkspace);
        }
    }

    private void updateButtonAppearance(JButton btn, boolean active) {
        DesktopTheme t = currentTheme;
        if (active) {
            btn.setBackground(t.sunkenBg());
            btn.setForeground(t.textActive());
            btn.setBorder(new MotifBevelBorder(false, 2));
        } else {
            btn.setBackground(t.base());
            btn.setForeground(t.textActive());
            btn.setBorder(new MotifBevelBorder(true, 2));
        }
    }

    @Override
    public JComponent component() {
        return pad;
    }

    @Override
    protected void updateThemeStyles(DesktopTheme theme) {
        pad.setBackground(theme.sunkenBg());
        leftStatus.setBackground(theme.sunkenBg());
        grid.setBackground(theme.sunkenBg());
        rightStatus.setBackground(theme.sunkenBg());

        lockBtn.setBackground(theme.base());
        lockBtn.setBorder(new MotifBevelBorder(true, 1));
        lockBtn.setIcon(IconManager.getIcon("lock", 11));

        powerBtn.setBackground(theme.base());
        powerBtn.setBorder(new MotifBevelBorder(true, 1));
        powerBtn.setIcon(IconManager.getIcon("power", 11));

        updateAllButtonAppearances();
    }
}

