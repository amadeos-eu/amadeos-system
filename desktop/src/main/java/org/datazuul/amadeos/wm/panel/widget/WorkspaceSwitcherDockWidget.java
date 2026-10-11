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
import java.awt.event.ActionListener;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import org.datazuul.amadeos.wm.theme.DesktopTheme;

/**
 * Authentic CDE 4-way Workspace Switcher & Session Control Pad:
 * - Raised outer plateau block on the main panel layer.
 * - Left pillar: Flat lock button with authentic carved CDE padlock symbol.
 * - Center tray: Sunken 3D teal/slate tray holding 2x2 grid of workspace buttons ("One", "Two", "Three", "Four").
 * - Right pillar: Mail/activity LED indicator slit at top, flat "EXIT" button at bottom.
 */
public final class WorkspaceSwitcherDockWidget extends AbstractDockWidget {
    private final JPanel pad;
    private final JPanel leftPillar;
    private final JPanel centerTray;
    private final JPanel rightPillar;
    private final JPanel grid;
    final JButton[] workspaceButtons = new JButton[4];
    final FlatLockButton lockBtn;
    final FlatExitButton exitBtn;
    final StatusIndicator statusIndicator;
    private int activeWorkspace = 1;
    private final Consumer<Integer> workspaceListener;
    private final Runnable powerAction;

    public WorkspaceSwitcherDockWidget(Consumer<Integer> workspaceListener, Runnable powerAction) {
        super("workspace-switcher");
        this.workspaceListener = workspaceListener;
        this.powerAction = powerAction;

        // 1. Outer raised plateau container (flat higher-leveled layer on the main panel)
        pad = new JPanel(new BorderLayout(0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                DesktopTheme t = currentTheme;
                int w = getWidth();
                int h = getHeight();

                // 3D raised plateau border
                g.setColor(t.highlight());
                g.drawLine(0, 0, w - 1, 0);
                g.drawLine(0, 0, 0, h - 1);

                g.setColor(t.darkShadow());
                g.drawLine(0, h - 1, w - 1, h - 1);
                g.drawLine(w - 1, 0, w - 1, h - 1);
            }
        };
        pad.setPreferredSize(new Dimension(220, CdePanelChrome.SLOT_HEIGHT));
        pad.setBackground(currentTheme.base());

        // 2. Left Pillar: Flat Lock Button
        leftPillar = new JPanel(new GridLayout(2, 1, 0, 0));
        leftPillar.setOpaque(false);
        leftPillar.setPreferredSize(new Dimension(28, CdePanelChrome.SLOT_HEIGHT));

        lockBtn = new FlatLockButton(e -> JOptionPane.showMessageDialog(pad, "Screen Lock is active.", "Lock", JOptionPane.INFORMATION_MESSAGE));
        leftPillar.add(lockBtn);

        JPanel leftSpacer = new JPanel();
        leftSpacer.setOpaque(false);
        leftPillar.add(leftSpacer);

        pad.add(leftPillar, BorderLayout.WEST);

        // 3. Center Tray: Sunken 3D Teal/Slate Workspace Box
        centerTray = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                DesktopTheme t = currentTheme;
                int w = getWidth();
                int h = getHeight();

                // Sunken 3D bezel around teal tray
                g.setColor(t.darkShadow());
                g.drawLine(0, 0, w - 1, 0);
                g.drawLine(0, 1, w - 1, 1);
                g.drawLine(0, 0, 0, h - 1);
                g.drawLine(1, 0, 1, h - 1);

                g.setColor(t.highlight());
                g.drawLine(0, h - 1, w - 1, h - 1);
                g.drawLine(w - 1, 0, w - 1, h - 1);
                g.setColor(t.softHighlight());
                g.drawLine(1, h - 2, w - 2, h - 2);
                g.drawLine(w - 2, 1, w - 2, h - 2);
            }
        };
        centerTray.setBackground(currentTheme.activeTitleBg());
        centerTray.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        grid = new JPanel(new GridLayout(2, 2, 2, 2));
        grid.setOpaque(false);

        String[] wsNames = {"One", "Two", "Three", "Four"};
        for (int i = 0; i < 4; i++) {
            int wsNum = i + 1;
            JButton btn = new JButton(wsNames[i]);
            btn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            btn.setFocusPainted(false);
            btn.setMargin(new Insets(1, 2, 1, 2));

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
        centerTray.add(grid, BorderLayout.CENTER);
        pad.add(centerTray, BorderLayout.CENTER);

        // 4. Right Pillar: Status/Mail Indicator Slit + Flat EXIT Button
        rightPillar = new JPanel(new GridLayout(2, 1, 0, 0));
        rightPillar.setOpaque(false);
        rightPillar.setPreferredSize(new Dimension(30, CdePanelChrome.SLOT_HEIGHT));

        statusIndicator = new StatusIndicator();
        rightPillar.add(statusIndicator);

        exitBtn = new FlatExitButton(e -> {
            if (this.powerAction != null) {
                this.powerAction.run();
            }
        });
        rightPillar.add(exitBtn);

        pad.add(rightPillar, BorderLayout.EAST);

        updateThemeStyles(currentTheme);
    }

    private void updateAllButtonAppearances() {
        for (int i = 0; i < 4; i++) {
            updateButtonAppearance(workspaceButtons[i], (i + 1) == activeWorkspace);
        }
    }

    private void updateButtonAppearance(JButton btn, boolean active) {
        DesktopTheme t = currentTheme;
        btn.setSelected(active);
        if (active) {
            btn.setBackground(t.activeTitleBg());
            btn.setForeground(Color.WHITE);
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
        pad.setBackground(theme.base());
        leftPillar.setBackground(theme.base());
        centerTray.setBackground(theme.activeTitleBg());
        rightPillar.setBackground(theme.base());

        lockBtn.repaint();
        statusIndicator.repaint();
        exitBtn.repaint();

        updateAllButtonAppearances();
    }

    /**
     * Flat CDE Lock Button with an authentic carved 3D padlock symbol.
     */
    final class FlatLockButton extends JButton {
        FlatLockButton(ActionListener action) {
            setFocusPainted(false);
            setRolloverEnabled(true);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setToolTipText("Lock Screen");
            if (action != null) {
                addActionListener(action);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth();
            int h = getHeight();
            ButtonModel model = getModel();
            boolean isPressed = model.isArmed() && model.isPressed();
            boolean isHovered = model.isRollover();

            DesktopTheme t = currentTheme;

            if (isPressed) {
                g2.setColor(t.sunkenBg());
                g2.fillRect(1, 1, w - 2, h - 2);

                g2.setColor(t.darkShadow());
                g2.drawLine(0, 0, w - 1, 0);
                g2.drawLine(0, 0, 0, h - 1);
                g2.setColor(t.highlight());
                g2.drawLine(0, h - 1, w - 1, h - 1);
                g2.drawLine(w - 1, 0, w - 1, h - 1);
            } else if (isHovered) {
                g2.setColor(t.softHighlight());
                g2.fillRect(1, 1, w - 2, h - 2);
            }

            int ox = (w - 15) / 2;
            int oy = (h - 18) / 2;
            if (isPressed) {
                ox += 1;
                oy += 1;
            }

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            // Shackle
            g2.setColor(t.darkShadow());
            g2.drawLine(ox + 5, oy, ox + 9, oy);
            g2.drawLine(ox + 3, oy + 2, ox + 4, oy + 1);
            g2.drawLine(ox + 10, oy + 1, ox + 11, oy + 2);
            g2.drawLine(ox + 3, oy + 3, ox + 3, oy + 7);
            g2.drawLine(ox + 11, oy + 3, ox + 11, oy + 7);

            g2.setColor(Color.WHITE);
            g2.drawLine(ox + 5, oy + 1, ox + 9, oy + 1);
            g2.drawLine(ox + 4, oy + 2, ox + 5, oy + 2);
            g2.drawLine(ox + 4, oy + 3, ox + 4, oy + 7);

            g2.setColor(new Color(0xCA, 0xD1, 0xD9));
            g2.drawLine(ox + 9, oy + 2, ox + 10, oy + 2);
            g2.drawLine(ox + 10, oy + 3, ox + 10, oy + 7);

            g2.setColor(t.darkShadow());
            g2.drawLine(ox + 5, oy + 3, ox + 9, oy + 3);
            g2.drawLine(ox + 5, oy + 4, ox + 5, oy + 7);
            g2.drawLine(ox + 9, oy + 4, ox + 9, oy + 7);

            // Body outline
            g2.setColor(t.darkShadow());
            g2.drawRect(ox, oy + 7, 14, 10);

            // Highlight shadow
            g2.setColor(Color.WHITE);
            g2.drawLine(ox + 1, oy + 18, ox + 14, oy + 18);
            g2.drawLine(ox + 15, oy + 8, ox + 15, oy + 18);

            // Horizontal ribs
            g2.setColor(Color.WHITE);
            g2.drawLine(ox + 1, oy + 8, ox + 13, oy + 8);
            g2.setColor(t.shadow());
            g2.drawLine(ox + 1, oy + 9, ox + 13, oy + 9);
            g2.setColor(t.darkShadow());
            g2.drawLine(ox + 1, oy + 10, ox + 13, oy + 10);
            g2.setColor(Color.WHITE);
            g2.drawLine(ox + 1, oy + 11, ox + 13, oy + 11);
            g2.setColor(t.darkShadow());
            g2.drawLine(ox + 1, oy + 12, ox + 13, oy + 12);
            g2.setColor(Color.WHITE);
            g2.drawLine(ox + 1, oy + 13, ox + 13, oy + 13);
            g2.setColor(t.darkShadow());
            g2.drawLine(ox + 1, oy + 14, ox + 13, oy + 14);
            g2.setColor(Color.WHITE);
            g2.drawLine(ox + 1, oy + 15, ox + 13, oy + 15);
            g2.setColor(t.darkShadow());
            g2.drawLine(ox + 1, oy + 16, ox + 13, oy + 16);

            g2.dispose();
        }
    }

    /**
     * Flat CDE Exit Button featuring a crisp bordered box around "EXIT".
     */
    final class FlatExitButton extends JButton {
        FlatExitButton(ActionListener action) {
            setFocusPainted(false);
            setRolloverEnabled(true);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setToolTipText("Session & Exit Controls");
            if (action != null) {
                addActionListener(action);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth();
            int h = getHeight();
            ButtonModel model = getModel();
            boolean isPressed = model.isArmed() && model.isPressed();
            boolean isHovered = model.isRollover();

            DesktopTheme t = currentTheme;

            int ex = (w - 24) / 2;
            int ey = (h - 16) / 2;
            if (isPressed) {
                ex += 1;
                ey += 1;
            }

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            if (isPressed) {
                g2.setColor(t.sunkenBg());
                g2.fillRect(ex + 1, ey + 1, 22, 14);

                g2.setColor(t.darkShadow());
                g2.drawLine(ex, ey, ex + 23, ey);
                g2.drawLine(ex, ey, ex, ey + 15);
                g2.setColor(t.highlight());
                g2.drawLine(ex + 1, ey + 15, ex + 23, ey + 15);
                g2.drawLine(ex + 23, ey + 1, ex + 23, ey + 15);
            } else {
                if (isHovered) {
                    g2.setColor(t.softHighlight());
                    g2.fillRect(ex + 1, ey + 1, 22, 14);
                }

                // Crisp etched 1px box
                g2.setColor(t.darkShadow());
                g2.drawLine(ex, ey, ex + 23, ey);
                g2.drawLine(ex, ey, ex, ey + 15);
                g2.setColor(Color.WHITE);
                g2.drawLine(ex + 1, ey + 16, ex + 24, ey + 16);
                g2.drawLine(ex + 24, ey + 1, ex + 24, ey + 16);
                g2.setColor(t.darkShadow());
                g2.drawLine(ex + 23, ey + 1, ex + 23, ey + 15);
                g2.drawLine(ex + 1, ey + 15, ex + 23, ey + 15);
            }

            // Crisp "EXIT" bitmap letters
            g2.setColor(Color.BLACK);
            // E
            int tx = ex + 4;
            int ty = ey + 3;
            for (int y = ty; y <= ty + 9; y++) g2.drawLine(tx, y, tx, y);
            g2.drawLine(tx, ty, tx + 3, ty);
            g2.drawLine(tx, ty + 4, tx + 2, ty + 4);
            g2.drawLine(tx, ty + 9, tx + 3, ty + 9);

            // X
            tx = ex + 9;
            g2.drawLine(tx, ty, tx + 4, ty + 9);
            g2.drawLine(tx + 4, ty, tx, ty + 9);

            // I
            tx = ex + 15;
            for (int y = ty; y <= ty + 9; y++) g2.drawLine(tx, y, tx, y);

            // T
            tx = ex + 18;
            g2.drawLine(tx - 1, ty, tx + 3, ty);
            for (int y = ty; y <= ty + 9; y++) g2.drawLine(tx + 1, y, tx + 1, y);

            g2.dispose();
        }
    }

    /**
     * Authentic CDE Status & Activity Indicator Slit with yellow LED dashes.
     */
    final class StatusIndicator extends JComponent {
        StatusIndicator() {
            setOpaque(false);
            setToolTipText("System Activity & Status");
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth();
            int h = getHeight();
            DesktopTheme t = currentTheme;

            int sx = (w - 24) / 2;
            int sy = (h - 7) / 2;

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            // Sunken slit border
            g2.setColor(t.darkShadow());
            g2.drawLine(sx, sy, sx + 23, sy);
            g2.drawLine(sx, sy, sx, sy + 6);
            g2.setColor(Color.WHITE);
            g2.drawLine(sx + 1, sy + 7, sx + 24, sy + 7);
            g2.drawLine(sx + 24, sy + 1, sx + 24, sy + 7);

            // Dark interior
            g2.setColor(new Color(0x1E, 0x24, 0x2B));
            g2.fillRect(sx + 1, sy + 1, 23, 6);

            // Yellow LED dashes
            g2.setColor(new Color(0xFA, 0xCC, 0x15));
            for (int dx = sx + 2; dx <= sx + 21; dx += 2) {
                g2.drawLine(dx, sy + 3, dx, sy + 4);
            }

            g2.dispose();
        }
    }
}
