package eu.amadeos.system.settings;

import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.theme.ThemeManager;
import eu.amadeos.system.input.InputApplier;
import eu.amadeos.system.settings.SettingsStores.View;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.BevelBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * The Settings of the amadeOS desktop, CDE's Style Manager grown up: the controls (Color, Font, Backdrop,
 * Keyboard, ...) in a sidebar, grouped, with the chosen control's page next to it and a search field above.
 * View &gt; Compact gives back CDE's window, one button per control that opens the control in a dialog of its
 * own. Controls amadeOS does not offer yet are shown disabled.
 */
public final class SettingsWindow extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(SettingsWindow.class.getName());
    private static final String RESULTS_CARD = "search";
    private static final int SIDEBAR_ICON = 24;

    static {
        ThemeManager.currentTheme(); // installs the desktop look before the fields below are made
    }

    private final SettingsStores stores;
    private final SystemAccess system;
    private final InputControls input;
    private final JLabel status = new JLabel(" ");
    // Sidebar view: one page per control, created when first shown, kept until Reset or the window closes.
    private final Map<SettingsCategory, SettingsPage> pages = new EnumMap<>(SettingsCategory.class);
    private final Map<SettingsCategory, JComponent> pageViews = new EnumMap<>(SettingsCategory.class);
    private final Map<SettingsCategory, SidebarEntry> entries = new EnumMap<>(SettingsCategory.class);
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);
    private final JPanel results = new JPanel();
    private final JTextField search = new SearchField();
    private final JLabel crumb = new JLabel(" ");
    // Compact view: one button and at most one dialog per control.
    private final Map<SettingsCategory, ControlDialog> dialogs = new EnumMap<>(SettingsCategory.class);
    private final Map<SettingsCategory, AbstractButton> buttons = new EnumMap<>(SettingsCategory.class);
    private final JRadioButtonMenuItem sidebarItem = new JRadioButtonMenuItem();
    private final JRadioButtonMenuItem compactItem = new JRadioButtonMenuItem();
    private View view;
    private SettingsCategory selected = SettingsCategory.COLOR;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> open(args.length > 0 ? args[0] : null));
    }

    /**
     * Opens a Settings window for the current user; call on the EDT.
     *
     * @param control the control to show first, e.g. {@code backdrop}, or null
     */
    public static SettingsWindow open(String control) {
        SettingsWindow window = new SettingsWindow(SettingsStores.user(), new InputApplier(), SystemAccess.system());
        if (control != null) {
            for (SettingsCategory category : SettingsCategory.values()) {
                if (category.name().equalsIgnoreCase(control) && category.available()) window.openControl(category);
            }
            // The proxy has its own tab on the Network page; programs that send the user there name it.
            if (control.equalsIgnoreCase("proxy")) window.openProxy();
        }
        window.setVisible(true);
        return window;
    }

    SettingsWindow(SettingsStores stores, InputApplier applier, SystemAccess system) {
        super(SettingsTexts.get("title"));
        this.stores = stores;
        this.system = system;
        this.input = new InputControls(stores.input(), applier);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(menuBar());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeControls();
            }
        });
        // The Settings change the theme and the font size themselves; restyle their windows like any application.
        ThemeManager.addListener(theme -> SwingUtilities.invokeLater(() -> AmadeUI.apply(theme)));
        showView(stores.view());
        setLocationByPlatform(true);
    }

    private JMenuBar menuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu file = Mnemonics.apply(new JMenu(), SettingsTexts.get("menu.file"));
        JMenuItem exit = Mnemonics.apply(new JMenuItem(), SettingsTexts.get("menu.exit"));
        exit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK));
        exit.addActionListener(e -> {
            closeControls();
            dispose();
        });
        file.add(exit);

        JMenu viewMenu = Mnemonics.apply(new JMenu(), SettingsTexts.get("menu.view"));
        Mnemonics.apply(sidebarItem, SettingsTexts.get("menu.view.sidebar"));
        Mnemonics.apply(compactItem, SettingsTexts.get("menu.view.compact"));
        ButtonGroup group = new ButtonGroup();
        group.add(sidebarItem);
        group.add(compactItem);
        sidebarItem.addActionListener(e -> switchView(View.SIDEBAR));
        compactItem.addActionListener(e -> switchView(View.COMPACT));
        viewMenu.add(sidebarItem);
        viewMenu.add(compactItem);

        JMenu help = Mnemonics.apply(new JMenu(), SettingsTexts.get("menu.help"));
        JMenuItem about = Mnemonics.apply(new JMenuItem(), SettingsTexts.get("menu.about"));
        about.addActionListener(e -> JOptionPane.showMessageDialog(this, SettingsTexts.get("about.text"),
                Mnemonics.strip(SettingsTexts.get("menu.about")), JOptionPane.INFORMATION_MESSAGE));
        help.add(about);
        bar.add(file);
        bar.add(viewMenu);
        bar.add(help);
        return bar;
    }

    // ------------------------------------------------------------------------------------------- views

    /** Changes the view and remembers it; open controls are closed without applying. */
    void switchView(View newView) {
        if (newView == view) return;
        closeControls();
        try {
            stores.saveView(newView);
        } catch (RuntimeException e) {
            LOGGER.warning("Cannot save the view: " + e.getMessage());
        }
        showView(newView);
        setLocationRelativeTo(null);
    }

    private void showView(View newView) {
        view = newView;
        (newView == View.COMPACT ? compactItem : sidebarItem).setSelected(true);
        JPanel content = new JPanel(new BorderLayout());
        content.add(newView == View.COMPACT ? compactView() : sidebarView(), BorderLayout.CENTER);
        content.add(statusBar(), BorderLayout.SOUTH);
        setContentPane(content);
        setStatus(null);
        if (newView == View.COMPACT) {
            setResizable(false);
            pack();
        } else {
            setResizable(true);
            pack();
            Dimension size = getSize();
            setSize(Math.max(size.width, 960), Math.max(size.height, 680));
            setMinimumSize(new Dimension(640, 460));
            select(selected);
        }
    }

    View view() {
        return view;
    }

    private JComponent statusBar() {
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        statusBar.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        AmadeUI.style(status, (l, t) -> l.setFont(UIManager.getFont("Label.font").deriveFont(AmadeUI.scaled(11f))));
        statusBar.add(status);
        return statusBar;
    }

    /** Closes the dialogs of the compact view and drops the pages of the sidebar view, undoing their previews. */
    private void closeControls() {
        for (SettingsPage page : pages.values()) page.cancelled();
        pages.clear();
        pageViews.clear();
        cardPanel.removeAll();
        for (ControlDialog dialog : List.copyOf(dialogs.values())) {
            dialog.page().cancelled();
            dialog.dispose();
        }
        dialogs.clear();
    }

    private SettingsPage createPage(SettingsCategory category) {
        return switch (category) {
            case COLOR -> new ColorPage(this::setStatus);
            case FONT -> new FontPage(this::setStatus);
            case BACKDROP -> new BackdropPage(this::setStatus);
            case WORKSPACES -> new WorkspacesPage(this::setStatus, stores.workspaces(),
                    () -> openControl(SettingsCategory.BACKDROP));
            case PANEL -> new PanelPage(this::setStatus, stores.panel());
            case KEYBOARD -> new KeyboardPage(this::setStatus, input);
            case MOUSE -> new MousePage(this::setStatus, input);
            case SOUND -> new SoundPage(this::setStatus, system.sound(), system.soundTest());
            case BEEP -> new BeepPage(this::setStatus, input);
            case DISPLAY -> new DisplayPage(this::setStatus, system.displays(), stores.display());
            case SCREEN -> new ScreenPage(this::setStatus, stores.screen());
            case WINDOW -> new WindowPage(this::setStatus, stores.window());
            case CLIPBOARD -> new ClipboardPage(this::setStatus, stores.clipboard());
            case STARTUP -> new StartupPage(this::setStatus, stores.startup(), stores.autostart(), stores.servicesDir(),
                    system.applications());
            case NETWORK -> new NetworkPage(this::setStatus, stores.proxy(), system.packages(), stores,
                    system.proxyCheck(), System.getenv(), system.network());
            case BLUETOOTH -> new BluetoothPage(this::setStatus, system.bluetooth());
            case PRINTERS -> new PrintersPage(this::setStatus, system.printers(), system.launcher());
            case USERS -> new UsersPage(this::setStatus, system.users());
            case PASSWORDS -> new PasswordsPage(this::setStatus, system.keyring());
            case UPDATES -> new UpdatesPage(this::setStatus, system.packages(), system.launcher());
            case LANGUAGE -> new LanguagePage(this::setStatus, stores.locales(), system.timeZones());
        };
    }

    // ------------------------------------------------------------------------------------- compact view

    // --8<-- [start:compact]
    private JComponent compactView() {
        buttons.clear();
        // CDE's row had nine buttons; with amadeOS's controls it takes two rows.
        JPanel row = new JPanel(new GridLayout(2, 0, 4, 4));
        row.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        for (SettingsCategory category : SettingsCategory.values()) {
            JButton button = new JButton(category.label(), SettingsIcons.of(category));
            button.setVerticalTextPosition(SwingConstants.BOTTOM);
            button.setHorizontalTextPosition(SwingConstants.CENTER);
            AmadeUI.style(button, (b, t) -> b.setFont(UIManager.getFont("Button.font").deriveFont(AmadeUI.scaled(11f))));
            button.setEnabled(category.available());
            button.setToolTipText(tooltip(category));
            button.addActionListener(e -> openControl(category));
            buttons.put(category, button);
            row.add(button);
        }
        return row;
    }
    // --8<-- [end:compact]

    private static String tooltip(SettingsCategory category) {
        return category.available() ? category.description()
                : SettingsTexts.format("category.unavailable", category.description());
    }

    /**
     * Shows a control: in the sidebar view its page, in the compact view its dialog, brought to the front if it is
     * already open. Controls that are not available do nothing.
     */
    public void openControl(SettingsCategory category) {
        if (!category.available()) return;
        if (view == View.SIDEBAR) {
            search.setText("");
            select(category);
            return;
        }
        ControlDialog existing = dialogs.get(category);
        if (existing != null && existing.isDisplayable()) {
            existing.toFront();
            return;
        }
        SettingsPage page = createPage(category);
        if (page == null) return;
        ControlDialog dialog = new ControlDialog(this, page);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                dialogs.remove(category, dialog);
            }
        });
        dialogs.put(category, dialog);
        dialog.setVisible(true);
    }

    // ------------------------------------------------------------------------------------- sidebar view

    private JComponent sidebarView() {
        entries.clear();
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        SettingsCategory.Group group = null;
        for (SettingsCategory category : SettingsCategory.values()) {
            if (category.group() != group) {
                group = category.group();
                list.add(groupHeader(group.label(), list.getComponentCount() == 0));
            }
            SidebarEntry entry = new SidebarEntry(category);
            entries.put(category, entry);
            list.add(entry);
        }
        list.add(Box.createVerticalGlue());
        list.setFocusable(true);
        list.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "previous");
        list.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "next");
        list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "previous");
        list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "next");
        list.getActionMap().put("previous", step(-1));
        list.getActionMap().put("next", step(1));

        JScrollPane side = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        int width = Math.max(190, list.getPreferredSize().width + 24);
        side.setPreferredSize(new Dimension(width, 10));

        results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
        results.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        cardPanel.removeAll();
        cardPanel.add(results, RESULTS_CARD);

        search.setPreferredSize(new Dimension(width, search.getPreferredSize().height + 4));
        search.setToolTipText(SettingsTexts.get("search.tooltip"));
        search.getAccessibleContext().setAccessibleName(SettingsTexts.get("search.placeholder"));
        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));
        top.add(search, BorderLayout.WEST);
        top.add(crumb, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout());
        right.setBorder(AmadeUI.raised());
        right.add(cardPanel, BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(8, 0));
        center.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        center.add(side, BorderLayout.WEST);
        center.add(right, BorderLayout.CENTER);

        JPanel root = new JPanel(new BorderLayout());
        root.add(top, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        // Ctrl+F finds a setting, as in most programs.
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "find");
        root.getActionMap().put("find", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                search.requestFocusInWindow();
                search.selectAll();
            }
        });
        return root;
    }

    private AbstractAction step(int direction) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                SettingsCategory[] all = SettingsCategory.values();
                int i = selected.ordinal();
                do {
                    i += direction;
                } while (i >= 0 && i < all.length && !all[i].available());
                if (i >= 0 && i < all.length) select(all[i]);
            }
        };
    }

    private static JComponent groupHeader(String text, boolean first) {
        JLabel label = new JLabel(text.toUpperCase(java.util.Locale.getDefault()));
        AmadeUI.style(label, (l, t) -> {
            l.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD, AmadeUI.scaled(10f)));
            l.setForeground(t.textInactive());
        });
        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(first ? 2 : 10, 4, 3, 4));
        header.add(label, BorderLayout.WEST);
        JPanel line = new JPanel(new BorderLayout());
        line.setOpaque(false);
        line.add(new JSeparator(), BorderLayout.CENTER);
        header.add(line, BorderLayout.CENTER);
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, header.getPreferredSize().height));
        return header;
    }

    /** Opens the Network page at its Proxy tab. */
    void openProxy() {
        openControl(SettingsCategory.NETWORK);
        SettingsPage page = view == View.SIDEBAR ? pages.get(SettingsCategory.NETWORK)
                : dialogs.containsKey(SettingsCategory.NETWORK) ? dialogs.get(SettingsCategory.NETWORK).page() : null;
        if (page instanceof NetworkPage network) network.tabs().setSelectedIndex(1);
    }

    /** Shows the page of a control in the sidebar view. */
    void select(SettingsCategory category) {
        if (view != View.SIDEBAR || !category.available()) return;
        selected = category;
        for (SidebarEntry entry : entries.values()) {
            entry.dimmed = false;
            entry.repaint();
        }
        if (!pageViews.containsKey(category)) {
            SettingsPage page = createPage(category);
            if (page == null) return;
            pages.put(category, page);
            JComponent pageView = pageView(page);
            pageViews.put(category, pageView);
            cardPanel.add(pageView, category.name());
        }
        cards.show(cardPanel, category.name());
        crumb.setText(SettingsTexts.format("crumb", category.group().label(), category.label()));
        cardPanel.revalidate();
    }

    /** The control shown in the sidebar view. */
    SettingsCategory selected() {
        return selected;
    }

    /** The page of a control in the sidebar view, once it was shown. */
    SettingsPage page(SettingsCategory category) {
        return pages.get(category);
    }

    // --8<-- [start:page]
    private JComponent pageView(SettingsPage page) {
        SettingsCategory category = page.category();
        JLabel title = new JLabel(category.label());
        AmadeUI.style(title, (l, t) -> l.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD, AmadeUI.scaled(17f))));
        JLabel description = new JLabel(category.description());
        AmadeUI.style(description, (l, t) -> l.setFont(UIManager.getFont("Label.font").deriveFont(Font.PLAIN)));
        JPanel texts = new JPanel(new GridLayout(2, 1));
        texts.add(title);
        texts.add(description);
        JPanel head = new JPanel(new BorderLayout(12, 0));
        head.add(new JLabel(SettingsIcons.of(category)), BorderLayout.WEST);
        head.add(texts, BorderLayout.CENTER);
        JPanel north = new JPanel(new BorderLayout(0, 8));
        north.add(head, BorderLayout.CENTER);
        north.add(new JSeparator(), BorderLayout.SOUTH);

        JPanel holder = new WidthTrackingPanel();
        holder.add(page, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        JButton apply = new JButton(SettingsTexts.get("button.apply"));
        apply.addActionListener(e -> page.applyChanges());
        JButton reset = new JButton(SettingsTexts.get("button.reset"));
        reset.setToolTipText(SettingsTexts.get("button.reset.tooltip"));
        reset.addActionListener(e -> reset(category));
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonRow.add(apply);
        buttonRow.add(reset);
        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.add(new JSeparator(), BorderLayout.NORTH);
        south.add(buttonRow, BorderLayout.CENTER);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 18, 10, 18));
        root.add(north, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        // Pages that act at once (connecting a device) have nothing to apply or reset.
        if (!category.immediate()) root.add(south, BorderLayout.SOUTH);
        return root;
    }
    // --8<-- [end:page]

    /** Throws away what was changed on a page and shows the saved settings again. */
    void reset(SettingsCategory category) {
        SettingsPage page = pages.remove(category);
        JComponent pageView = pageViews.remove(category);
        if (page != null) page.cancelled();
        if (pageView != null) cardPanel.remove(pageView);
        select(category);
        setStatus(SettingsTexts.format("status.reset", category.label()));
    }

    // ------------------------------------------------------------------------------------------- search

    /** Shows the controls that match the search text; an empty text shows the selected page again. */
    void search(String text) {
        if (view != View.SIDEBAR) return;
        String query = text == null ? "" : text.strip();
        if (query.isEmpty()) {
            select(selected);
            setStatus(null);
            return;
        }
        results.removeAll();
        JLabel heading = new JLabel(SettingsTexts.format("search.title", query));
        AmadeUI.style(heading, (l, t) -> l.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD, AmadeUI.scaled(16f))));
        heading.setAlignmentX(LEFT_ALIGNMENT);
        results.add(heading);
        results.add(Box.createVerticalStrut(10));
        List<SettingsCategory> found = matches(query);
        for (SidebarEntry entry : entries.values()) {
            entry.dimmed = !found.contains(entry.category);
            entry.repaint();
        }
        for (SettingsCategory category : found) {
            results.add(resultRow(category));
            results.add(Box.createVerticalStrut(4));
        }
        if (found.isEmpty()) {
            JLabel none = new JLabel(SettingsTexts.get("search.none"));
            none.setAlignmentX(LEFT_ALIGNMENT);
            results.add(none);
        }
        results.add(Box.createVerticalGlue());
        results.revalidate();
        results.repaint();
        crumb.setText(SettingsTexts.format("crumb", SettingsTexts.get("search.crumb"),
                SettingsTexts.format("search.count", found.size())));
        setStatus(SettingsTexts.get(found.isEmpty() ? "search.status.none" : "search.status"));
        cards.show(cardPanel, RESULTS_CARD);
    }

    /** The available controls whose name, description or keywords contain the text. */
    static List<SettingsCategory> matches(String query) {
        List<SettingsCategory> found = new ArrayList<>();
        for (SettingsCategory category : SettingsCategory.values()) {
            if (category.available() && category.matches(query)) found.add(category);
        }
        return found;
    }

    private JComponent resultRow(SettingsCategory category) {
        JButton row = new JButton("<html><b>" + category.label() + "</b><br>" + category.description() + "</html>",
                SettingsIcons.of(category, 28));
        row.setHorizontalAlignment(SwingConstants.LEADING);
        row.setIconTextGap(10);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        row.addActionListener(e -> openControl(category));
        return row;
    }

    void setStatus(String text) {
        status.setText(text == null || text.isBlank() ? " " : text);
    }

    String status() {
        return status.getText();
    }

    AbstractButton button(SettingsCategory category) {
        return buttons.get(category);
    }

    SidebarEntry entry(SettingsCategory category) {
        return entries.get(category);
    }

    JTextField searchField() {
        return search;
    }

    // ------------------------------------------------------------------------------------------ widgets

    /**
     * A control in the sidebar: icon and name; the shown one pushed in like an armed Motif toggle, with a bar in
     * the active title colour. Controls that do not match the search are drawn faint.
     */
    final class SidebarEntry extends JComponent {
        final SettingsCategory category;
        boolean dimmed;

        SidebarEntry(SettingsCategory category) {
            this.category = category;
            setEnabled(category.available());
            setToolTipText(tooltip(category));
            setAlignmentX(LEFT_ALIGNMENT);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (isEnabled()) openControl(category);
                }
            });
        }

        boolean isSelected() {
            return category == selected && !dimmed && cardShowsPage();
        }

        private boolean cardShowsPage() {
            JComponent pageView = pageViews.get(category);
            return pageView != null && pageView.isVisible();
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(entryFont(true));
            return new Dimension(SIDEBAR_ICON + 32 + fm.stringWidth(category.label()),
                    Math.max(SIDEBAR_ICON + 4, fm.getHeight() + 10));
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        private Font entryFont(boolean bold) {
            Font font = UIManager.getFont("Label.font");
            return font.deriveFont(bold ? Font.BOLD : Font.PLAIN);
        }

        @Override
        protected void paintComponent(Graphics g) {
            DesktopTheme t = AmadeUI.theme();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            boolean shown = isSelected();
            if (shown) {
                g2.setColor(t.sunkenBg());
                g2.fillRect(1, 1, w - 2, h - 2);
                AmadeUI.paintBevel(g2, 1, 1, w - 2, h - 2, false, 2);
                g2.setColor(t.activeTitleBg());
                g2.fillRect(3, 3, 4, h - 6);
            }
            if (dimmed) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
            SettingsIcons.of(category, SIDEBAR_ICON).paintIcon(this, g2, 12, (h - SIDEBAR_ICON) / 2);
            g2.setColor(isEnabled() ? t.textActive() : t.textInactive());
            g2.setFont(entryFont(shown));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(category.label(), SIDEBAR_ICON + 20, (h + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    /**
     * Holds a page in its scroll pane: as wide as the view while the page fits, so that lists and previews grow
     * with the window, and scrolling sideways only below the page's smallest width.
     */
    private static final class WidthTrackingPanel extends JPanel implements javax.swing.Scrollable {
        WidthTrackingPanel() {
            super(new BorderLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visible.height : visible.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return getParent() instanceof javax.swing.JViewport viewport && viewport.getWidth() >= getMinimumSize().width;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof javax.swing.JViewport viewport && viewport.getHeight() > getPreferredSize().height;
        }
    }

    /** A Motif text field with a magnifier and a hint while it is empty; typing searches. */
    private final class SearchField extends JTextField {
        SearchField() {
            setBorder(BorderFactory.createCompoundBorder(AmadeUI.sunken(), BorderFactory.createEmptyBorder(2, 24, 2, 4)));
            getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    search(getText());
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    search(getText());
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    search(getText());
                }
            });
            addActionListener(e -> {
                List<SettingsCategory> found = matches(getText());
                if (!found.isEmpty()) openControl(found.getFirst());
            });
            getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clear");
            getActionMap().put("clear", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    setText("");
                }
            });
        }

        @Override
        public void updateUI() {
            super.updateUI();
            setBorder(BorderFactory.createCompoundBorder(AmadeUI.sunken(), BorderFactory.createEmptyBorder(2, 24, 2, 4)));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color hint = AmadeUI.theme().textInactive();
            g2.setColor(hint);
            g2.setStroke(new BasicStroke(1.6f));
            int cy = getHeight() / 2;
            g2.draw(new Ellipse2D.Double(7, cy - 6, 9, 9));
            g2.drawLine(15, cy + 2, 19, cy + 6);
            if (getText().isEmpty()) {
                g2.setFont(getFont().deriveFont(Font.ITALIC));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(SettingsTexts.get("search.placeholder"), getInsets().left,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            }
            g2.dispose();
        }
    }
}
