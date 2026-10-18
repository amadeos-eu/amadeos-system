package eu.amadeos.panel.cde.widget;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleResolution;
import eu.amadeos.commons.i18n.LocaleStore;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.base.theme.backdrop.MotifColors;

/**
 * The CDE workspace switch, the raised box in the middle of the Front Panel:
 * <ul>
 * <li>Left column: the carved padlock (lock the screen), below it the desktop language (a carved code
 *     such as "DE" that opens the language menu; CDE left this corner empty).</li>
 * <li>Middle: the four workspace buttons "One" to "Four" in two rows, straight on the panel colour with
 *     gaps between them, as in CDE. Each button is a Motif push button filled with its workspace's colour
 *     from the theme ({@link DesktopTheme#workspaceColor(int)}: blue-grey, dialog colour, teal and rose in
 *     the default palette), with its label in the Motif foreground of that colour; the current workspace
 *     is the one pressed in.</li>
 * <li>Right column: the busy light (a sunken slit with yellow dashes) at the top, "EXIT" in an etched box
 *     at the bottom.</li>
 * </ul>
 */
public final class WorkspaceSwitcherDockWidget extends AbstractDockWidget {
    private static final Logger LOGGER = Logger.getLogger(WorkspaceSwitcherDockWidget.class.getName());
    static final String DESKTOP_TEXTS = "eu.amadeos.i18n.desktop";
    /** Width of the lock column on the left and the EXIT column on the right, as roomy as in CDE. */
    static final int COLUMN_WIDTH = 40;
    /** Width of the switch box: two 96px buttons with CDE's gaps between the lock and EXIT columns. */
    static final int PAD_WIDTH = 2 * COLUMN_WIDTH + 212;
    /** Thickness of a workspace button's Motif bevel. */
    private static final int BEVEL = 2;
    static final String[] WORKSPACE_NAMES = {"One", "Two", "Three", "Four"};
    private final JPanel pad;
    private final JPanel leftPillar;
    private final JPanel rightPillar;
    private final JPanel grid;
    final WorkspaceButton[] workspaceButtons = new WorkspaceButton[4];
    final FlatLockButton lockBtn;
    final LanguageButton languageBtn;
    private final LocaleStore locales;
    final FlatExitButton exitBtn;
    final StatusIndicator statusIndicator;
    private int activeWorkspace = 1;
    private final Consumer<Integer> workspaceListener;
    private final Runnable powerAction;

    public WorkspaceSwitcherDockWidget(Consumer<Integer> workspaceListener, Runnable powerAction) {
        this(workspaceListener, powerAction, systemLocales());
    }

    private static LocaleStore systemLocales() {
        LocaleStore store = LocaleStore.system();
        store.watch();
        return store;
    }

    /** @param locales the desktop language, shown and switched by the button under the lock */
    public WorkspaceSwitcherDockWidget(Consumer<Integer> workspaceListener, Runnable powerAction, LocaleStore locales) {
        super("workspace-switcher");
        this.locales = locales;
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
        pad.setPreferredSize(new Dimension(PAD_WIDTH, CdePanelChrome.SLOT_HEIGHT));
        pad.setBackground(currentTheme.base());

        // 2. Left Pillar: Flat Lock Button
        leftPillar = new JPanel(new GridLayout(2, 1, 0, 0));
        leftPillar.setOpaque(false);
        leftPillar.setPreferredSize(new Dimension(COLUMN_WIDTH, CdePanelChrome.SLOT_HEIGHT));

        lockBtn = new FlatLockButton(e -> eu.amadeos.base.process.LockLauncher.lockScreen());
        leftPillar.add(lockBtn);

        languageBtn = new LanguageButton();
        leftPillar.add(languageBtn);
        locales.addListener(locale -> SwingUtilities.invokeLater(languageBtn::refresh));

        pad.add(leftPillar, BorderLayout.WEST);

        // 3. Middle: the 2x2 workspace buttons straight on the panel colour, with CDE's gaps
        grid = new JPanel(new GridLayout(2, 2, 8, 8));
        grid.setOpaque(false);
        grid.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        for (int i = 0; i < 4; i++) {
            int wsNum = i + 1;
            WorkspaceButton btn = new WorkspaceButton(i, WORKSPACE_NAMES[i]);
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

        // 4. Right Pillar: Status/Mail Indicator Slit + Flat EXIT Button
        rightPillar = new JPanel(new GridLayout(2, 1, 0, 0));
        rightPillar.setOpaque(false);
        rightPillar.setPreferredSize(new Dimension(COLUMN_WIDTH, CdePanelChrome.SLOT_HEIGHT));

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

    private void updateButtonAppearance(WorkspaceButton btn, boolean active) {
        btn.setSelected(active);
        btn.setBackground(currentTheme.workspaceColor(btn.index));
        btn.repaint();
    }

    public int activeWorkspace() {
        return activeWorkspace;
    }

    public void setActiveWorkspace(int wsNum) {
        if (wsNum >= 1 && wsNum <= 4 && wsNum != activeWorkspace) {
            this.activeWorkspace = wsNum;
            updateAllButtonAppearances();
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
        rightPillar.setBackground(theme.base());

        lockBtn.repaint();
        languageBtn.repaint();
        statusIndicator.repaint();
        exitBtn.repaint();

        updateAllButtonAppearances();
    }

    /**
     * A workspace button: a Motif push button filled with the workspace's colour from the theme, its label
     * in that colour's Motif foreground. Raised when the workspace is not the current one, pressed in (bevel
     * swapped) when it is or while the mouse holds it.
     */
    final class WorkspaceButton extends JButton {
        final int index;

        WorkspaceButton(int index, String name) {
            super(name);
            this.index = index;
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            setBackground(currentTheme.workspaceColor(index));
            setToolTipText("Workspace " + name);
        }

        /** The workspace colour the button is painted with. */
        Color color() {
            return getBackground();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            int w = getWidth();
            int h = getHeight();
            ButtonModel model = getModel();
            boolean sunken = isSelected() || (model.isArmed() && model.isPressed());
            MotifColors colors = MotifColors.of(color());

            g2.setColor(colors.background());
            g2.fillRect(0, 0, w, h);
            Color topLeft = sunken ? colors.bottomShadow() : colors.topShadow();
            Color bottomRight = sunken ? colors.topShadow() : colors.bottomShadow();
            for (int i = 0; i < BEVEL; i++) {
                g2.setColor(topLeft);
                g2.drawLine(i, i, w - 1 - i, i);
                g2.drawLine(i, i, i, h - 1 - i);
                g2.setColor(bottomRight);
                g2.drawLine(i, h - 1 - i, w - 1 - i, h - 1 - i);
                g2.drawLine(w - 1 - i, i, w - 1 - i, h - 1 - i);
            }

            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
            g2.setFont(getFont());
            java.awt.FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(getText())) / 2 + (sunken ? 1 : 0);
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent() + (sunken ? 1 : 0);
            g2.setColor(colors.foreground());
            g2.drawString(getText(), tx, ty);
            g2.dispose();
        }
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

    /** The language the desktop uses now; English if none is set. */
    Language desktopLanguage() {
        return locales.language().orElseGet(
                () -> LocaleResolution.resolve(Optional.empty(), Optional.empty(), System.getenv()).language());
    }

    /** Builds the language menu: the five languages under their own names, then what a switch affects. */
    JPopupMenu languageMenu() {
        Language current = desktopLanguage();
        Messages messages = Messages.load(WorkspaceSwitcherDockWidget.class, DESKTOP_TEXTS, current.locale());
        JPopupMenu menu = new JPopupMenu();
        ButtonGroup group = new ButtonGroup();
        for (Language language : Language.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(language.nativeName(), language == current);
            item.setName(language.code());
            item.addActionListener(e -> switchLanguage(language));
            group.add(item);
            menu.add(item);
        }
        menu.addSeparator();
        for (String key : new String[]{"language.hint.apps", "language.hint.session"}) {
            JMenuItem hint = new JMenuItem(messages.get(key));
            hint.setEnabled(false);
            menu.add(hint);
        }
        return menu;
    }

    void switchLanguage(Language language) {
        try {
            locales.write(language);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Cannot store the desktop language", e);
        }
        languageBtn.refresh();
    }

    /**
     * The desktop language as a carved two-letter code in an etched box, like EXIT on the other side;
     * a click opens the language menu above the panel.
     */
    final class LanguageButton extends JButton {
        private String code = "";

        LanguageButton() {
            setFocusPainted(false);
            setRolloverEnabled(true);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            addActionListener(e -> {
                JPopupMenu menu = languageMenu();
                menu.show(this, 0, -menu.getPreferredSize().height);
            });
            refresh();
        }

        void refresh() {
            Language language = desktopLanguage();
            code = language.code().toUpperCase(Locale.ROOT);
            Messages messages = Messages.load(WorkspaceSwitcherDockWidget.class, DESKTOP_TEXTS, language.locale());
            setToolTipText(messages.format("language.tooltip", language.nativeName()));
            getAccessibleContext().setAccessibleName(getToolTipText());
            repaint();
        }

        String code() {
            return code;
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

            int bw = 22;
            int bh = 16;
            int bx = (w - bw) / 2;
            int by = (h - bh) / 2;
            if (isPressed) {
                bx += 1;
                by += 1;
            }
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            if (isPressed) {
                g2.setColor(t.sunkenBg());
                g2.fillRect(bx + 1, by + 1, bw - 2, bh - 2);
                g2.setColor(t.darkShadow());
                g2.drawLine(bx, by, bx + bw - 1, by);
                g2.drawLine(bx, by, bx, by + bh - 1);
                g2.setColor(t.highlight());
                g2.drawLine(bx + 1, by + bh - 1, bx + bw - 1, by + bh - 1);
                g2.drawLine(bx + bw - 1, by + 1, bx + bw - 1, by + bh - 1);
            } else {
                if (isHovered) {
                    g2.setColor(t.softHighlight());
                    g2.fillRect(bx + 1, by + 1, bw - 2, bh - 2);
                }
                // Etched 1px box, as around EXIT
                g2.setColor(t.darkShadow());
                g2.drawLine(bx, by, bx + bw - 1, by);
                g2.drawLine(bx, by, bx, by + bh - 1);
                g2.drawLine(bx + bw - 1, by + 1, bx + bw - 1, by + bh - 1);
                g2.drawLine(bx + 1, by + bh - 1, bx + bw - 1, by + bh - 1);
                g2.setColor(Color.WHITE);
                g2.drawLine(bx + 1, by + bh, bx + bw, by + bh);
                g2.drawLine(bx + bw, by + 1, bx + bw, by + bh);
            }

            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            java.awt.FontMetrics fm = g2.getFontMetrics();
            int tx = bx + (bw - fm.stringWidth(code)) / 2 + 1;
            int ty = by + (bh - fm.getHeight()) / 2 + fm.getAscent();
            g2.setColor(Color.BLACK);
            g2.drawString(code, tx, ty);
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
