package eu.amadeos.system.dm.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.net.InetAddress;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.HorizonUI;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.dm.session.LastUser;
import eu.amadeos.system.dm.session.KeyboardLayouts;
import eu.amadeos.system.dm.session.SessionType;
import eu.amadeos.system.dm.session.SystemTimeZone;
import eu.amadeos.system.dm.session.UserAccount;
import eu.amadeos.system.base.theme.ThemeManager;

/**
 * The login screen. It has the look of the session it will start: the Motif look with a bevelled card for the
 * Classic (CDE) session, the flat Horizon look with a rounded card over Horizon's picture for the Horizon
 * session, and for the desktop session the look the user had last time ({@link GreeterLook}). Choosing another
 * session or user switches the look at once.
 */
public final class AmadeLoginGreeter extends JFrame {
    public interface LoginCallback {
        /** @param timeZone the zone chosen for the system, e.g. {@code Europe/Berlin} */
        void onLogin(String username, String password, String sessionType, Language language, String timeZone);
    }

    private static final DateTimeFormatter CLOCK_FMT = DateTimeFormatter.ofPattern("EEE, MMM dd  HH:mm:ss");
    private static final String PROMPT = "Please enter your password to begin session.";
    /** Horizon's pictures, one per scheme; the image ships them, the greeter paints a gradient without them. */
    static final String HORIZON_PICTURES = "/usr/share/backgrounds/amadeos/horizon-";
    private static final int CARD_ARC = 22;

    private enum StatusKind { NORMAL, OK, ERROR }

    private final LoginCallback loginCallback;
    private final Consumer<String> powerActionCallback;
    private final JTextField userField;
    private final JPasswordField passField;
    private final JComboBox<String> sessionCombo;
    private final JComboBox<Language> languageCombo;
    private final JComboBox<String> timeZoneCombo;
    private final JComboBox<KeyboardLayouts.Layout> keyboardCombo;
    private final KeyboardLayouts.SystemLayout systemLayout = KeyboardLayouts.SYSTEM.system();
    private final JLabel statusLabel;
    private final JLabel clockLabel;
    private final JButton loginBtn;

    /** The greeter's own classic palette, for users who have none. */
    private final DesktopTheme classicTheme = GreeterLook.classic(ThemeManager.currentTheme());
    private DesktopTheme theme = classicTheme;
    private boolean horizon;
    private StatusKind statusKind = StatusKind.NORMAL;
    private final Map<String, Image> pictures = new HashMap<>();

    private final JPanel headerBar = new JPanel(new BorderLayout(10, 0)) {
        @Override
        protected void paintComponent(Graphics g) {
            if (!horizon) return;
            // Horizon: a slim dark bar along the top, like the top bar of the desktop
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(new Color(0, 0, 0, 110));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    };
    private final JLabel hostLabel = new JLabel();
    private final JPanel loginCard = new JPanel(new BorderLayout(0, 10)) {
        @Override
        protected void paintComponent(Graphics g) {
            if (horizon) paintHorizonCard((Graphics2D) g, this);
            else paintMotifCard(g, this);
        }
    };
    private final JPanel bannerPanel = new JPanel();
    private final Avatar avatar = new Avatar();
    private final JLabel titleText = new JLabel("amadeOS Desktop Login", SwingConstants.CENTER);
    private final JPanel formPanel = new JPanel(new GridBagLayout());
    private final List<JLabel> formLabels = new ArrayList<>();
    private final JPanel statusPlate = new JPanel(new BorderLayout()) {
        @Override
        protected void paintComponent(Graphics g) {
            if (horizon) return;
            super.paintComponent(g);
            // Sunken recessed plate
            g.setColor(theme.darkShadow());
            g.drawLine(0, 0, getWidth() - 1, 0);
            g.drawLine(0, 0, 0, getHeight() - 1);
            g.setColor(theme.highlight());
            g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight() - 1);
            g.drawLine(0, getHeight() - 1, getWidth() - 1, getHeight() - 1);
        }
    };
    private final JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    private final JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 10));
    private final JButton clearBtn = new JButton("Clear");
    private final JButton rebootBtn = new JButton("Reboot...");
    private final JButton poweroffBtn = new JButton("Shut Down...");
    /** The login screen on the primary monitor, see {@link #showOn}. */
    private final JPanel rootPanel = new JPanel(new BorderLayout()) {
        @Override
        protected void paintComponent(Graphics g) {
            paintBackdrop((Graphics2D) g, getWidth(), getHeight());
        }
    };
    /** The primary monitor's part of the X screen; null shows the login screen across the whole screen. */
    private Rectangle loginArea;

    public AmadeLoginGreeter(LoginCallback loginCallback, Consumer<String> powerActionCallback) {
        super("amadeOS Login Greeter");
        this.loginCallback = Objects.requireNonNull(loginCallback);
        this.powerActionCallback = powerActionCallback;

        setUndecorated(true);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setBounds(0, 0, screenSize.width, screenSize.height);

        // Root background: the classic UNIX workstation slate blue, or Horizon's picture, over all monitors;
        // the login screen itself (rootPanel) sits on the primary monitor (showOn)
        JPanel screenPanel = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                paintBackdrop((Graphics2D) g, getWidth(), getHeight());
            }

            @Override
            public void doLayout() {
                Rectangle area = loginArea != null ? loginArea.intersection(new Rectangle(getSize())) : null;
                rootPanel.setBounds(area != null && !area.isEmpty() ? area : new Rectangle(getSize()));
            }
        };
        setContentPane(screenPanel);
        screenPanel.add(rootPanel);

        // 1. TOP HEADER BAR
        headerBar.setOpaque(false);
        String hostName = "amadeos";
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {}
        hostLabel.setText("amadeOS UNIX Workstation  [" + hostName + "]");
        hostLabel.putClientProperty("host", hostName);
        headerBar.add(hostLabel, BorderLayout.WEST);

        clockLabel = new JLabel(LocalDateTime.now().format(CLOCK_FMT));
        headerBar.add(clockLabel, BorderLayout.EAST);
        rootPanel.add(headerBar, BorderLayout.NORTH);

        Timer clockTimer = new Timer(1000, e -> clockLabel.setText(LocalDateTime.now().format(CLOCK_FMT)));
        clockTimer.start();

        // 2. CENTER: the login card
        JPanel centerContainer = new JPanel(new GridBagLayout());
        centerContainer.setOpaque(false);
        centerContainer.add(loginCard, new GridBagConstraints());
        rootPanel.add(centerContainer, BorderLayout.CENTER);

        // 3. BOTTOM WORKSTATION CONTROLS BAR
        bottomBar.setOpaque(false);
        bottomBar.setBorder(BorderFactory.createEmptyBorder(0, 16, 12, 24));
        rebootBtn.addActionListener(e -> confirmPowerAction("reboot", "Restart the computer?"));
        poweroffBtn.addActionListener(e -> confirmPowerAction("poweroff", "Shut down and power off the computer?"));
        bottomBar.add(rebootBtn);
        bottomBar.add(poweroffBtn);
        rootPanel.add(bottomBar, BorderLayout.SOUTH);

        // Populate fields and set default focus
        // The user who logged in last, else the first regular user (the live user, or the one the installer
        // created); the greeter runs as root, so the state file under /var/lib is readable.
        this.userField = new JTextField(LastUser.SYSTEM.propose(), 16);
        this.passField = new JPasswordField(16);
        this.sessionCombo = new JComboBox<>(SessionType.labels());
        this.languageCombo = new JComboBox<>(Language.values());
        languageCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                Object text = value instanceof Language l ? l.nativeName() : value;
                return super.getListCellRendererComponent(list, text, index, selected, focus);
            }
        });
        languageCombo.setToolTipText("Language of the desktop and its applications");
        this.timeZoneCombo = new JComboBox<>(SystemTimeZone.SYSTEM.available().toArray(new String[0]));
        timeZoneCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                Object text = value instanceof String zone ? describeTimeZone(zone) : value;
                return super.getListCellRendererComponent(list, text, index, selected, focus);
            }
        });
        timeZoneCombo.setPrototypeDisplayValue("America/Argentina/Buenos_Aires");
        timeZoneCombo.setMaximumRowCount(16);
        timeZoneCombo.setToolTipText("Time zone of the computer's clock; set for the whole system at login");
        // The layouts of xkeyboard-config, starting with the computer's own (/etc/default/keyboard); a
        // choice switches the X server at once, so the password is typed with it
        List<KeyboardLayouts.Layout> layouts = KeyboardLayouts.SYSTEM.list();
        this.keyboardCombo = new JComboBox<>(layouts.toArray(new KeyboardLayouts.Layout[0]));
        keyboardCombo.setPrototypeDisplayValue(new KeyboardLayouts.Layout("latam", "Spanish (Latin American)"));
        keyboardCombo.setMaximumRowCount(16);
        keyboardCombo.setToolTipText("Keyboard layout, switched at once for typing the password");
        KeyboardLayouts.find(layouts, systemLayout.code()).ifPresent(keyboardCombo::setSelectedItem);
        keyboardCombo.addActionListener(e -> applySelectedKeyboardLayout());
        this.statusLabel = new JLabel(PROMPT, SwingConstants.CENTER);
        this.loginBtn = new JButton("  Log In  ");

        buildCardContent();

        // The look follows the session and, for the desktop session, the user
        sessionCombo.addActionListener(e -> followSession());
        userField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { avatar.repaint(); }
            @Override public void removeUpdate(DocumentEvent e) { avatar.repaint(); }
            @Override public void changedUpdate(DocumentEvent e) { avatar.repaint(); }
        });
        followSession();

        // Auto-focus password field and apply German layout when opened
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                applySelectedKeyboardLayout();
                preselectLanguage();
                preselectTimeZone();
                passField.requestFocusInWindow();
            }
        });
    }

    private void buildCardContent() {
        // Card title: the banner of the Motif card; Horizon shows the user's initial above it
        bannerPanel.setLayout(new BoxLayout(bannerPanel, BoxLayout.Y_AXIS));
        avatar.setAlignmentX(0.5f);
        titleText.setAlignmentX(0.5f);
        titleText.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        bannerPanel.add(avatar);
        bannerPanel.add(titleText);
        loginCard.add(bannerPanel, BorderLayout.NORTH);

        // Input form
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        userField.addActionListener(e -> passField.requestFocusInWindow());
        userField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                preselectLanguage();
                followSession();
            }
        });
        passField.addActionListener(e -> performLogin());
        addRow(gbc, 0, "User Name:", userField);
        addRow(gbc, 1, "Password:", passField);
        addRow(gbc, 2, "Session:", sessionCombo);
        // The language holds for the whole session (CDE: Options > Language)
        addRow(gbc, 3, "Language:", languageCombo);
        // A choice switches the keyboard at once
        addRow(gbc, 4, "Keyboard:", keyboardCombo);
        // The display manager runs as root, so this is where the system's zone is set
        addRow(gbc, 5, "Time Zone:", timeZoneCombo);

        // Status message: a recessed plate in the classic look, a line of text in Horizon
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        gbc.insets = new Insets(10, 4, 4, 4);
        statusPlate.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statusPlate.add(statusLabel, BorderLayout.CENTER);
        formPanel.add(statusPlate, gbc);
        loginCard.add(formPanel, BorderLayout.CENTER);

        // Bottom action buttons
        clearBtn.addActionListener(e -> {
            passField.setText("");
            showStatus(PROMPT, StatusKind.NORMAL);
            passField.requestFocusInWindow();
        });
        loginBtn.addActionListener(e -> performLogin());
        buttonRow.add(clearBtn);
        buttonRow.add(loginBtn);
        loginCard.add(buttonRow, BorderLayout.SOUTH);
    }

    private void addRow(GridBagConstraints gbc, int row, String text, JComponent field) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        JLabel label = new JLabel(text, SwingConstants.RIGHT);
        label.setLabelFor(field);
        formLabels.add(label);
        formPanel.add(label, gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        formPanel.add(field, gbc);
    }

    // ---- the look ----------------------------------------------------------------------------------

    /** The session field's choice. */
    SessionType selectedSession() {
        return SessionType.from((String) sessionCombo.getSelectedItem());
    }

    /** Takes the look of the chosen session, for the desktop session the user's last look. */
    private void followSession() {
        applyLook(GreeterLook.themeFor(selectedSession(), userHome(userField.getText().trim()), classicTheme));
    }

    /** Whether the greeter shows the Horizon look now. */
    boolean showsHorizon() {
        return horizon;
    }

    DesktopTheme theme() {
        return theme;
    }

    /** Shows the greeter in a theme's look: Motif for a classic palette, flat for a Horizon one. */
    void applyLook(DesktopTheme newTheme) {
        if (newTheme.equals(theme) && rootPane.getClientProperty("amade.look") != null) return;
        rootPane.putClientProperty("amade.look", newTheme.id());
        theme = newTheme;
        horizon = HorizonUI.active(newTheme);
        AmadeUI.install(newTheme);
        SwingUtilities.updateComponentTreeUI(this);
        style();
        getRootPane().setDefaultButton(horizon ? loginBtn : null);
        revalidate();
        repaint();
    }

    /** Colours, fonts and borders of the greeter's own parts for the current look. */
    private void style() {
        DesktopTheme t = theme;
        Color text = t.textActive();
        boolean flat = horizon;
        String host = String.valueOf(hostLabel.getClientProperty("host"));

        headerBar.setBorder(flat ? BorderFactory.createEmptyBorder(6, 16, 6, 16)
                : BorderFactory.createEmptyBorder(16, 24, 0, 24));
        hostLabel.setText(flat ? host : "amadeOS UNIX Workstation  [" + host + "]");
        hostLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, flat ? 13 : 15));
        hostLabel.setForeground(flat ? new Color(0xF0, 0xF1, 0xF6) : new Color(0xE0, 0xE8, 0xF0));
        clockLabel.setFont(flat ? new Font(Font.SANS_SERIF, Font.BOLD, 13) : new Font(Font.MONOSPACED, Font.BOLD, 14));
        clockLabel.setForeground(flat ? new Color(0xF0, 0xF1, 0xF6) : new Color(0xD0, 0xD8, 0xE0));

        loginCard.setOpaque(!flat);
        loginCard.setBackground(t.base());
        loginCard.setPreferredSize(flat ? new Dimension(540, 550) : new Dimension(480, 415));
        loginCard.setBorder(flat ? BorderFactory.createEmptyBorder(26, 30, 26, 30)
                : BorderFactory.createEmptyBorder(12, 16, 14, 16));

        avatar.setVisible(flat);
        bannerPanel.setOpaque(!flat);
        bannerPanel.setBackground(t.activeTitleBg());
        bannerPanel.setBorder(flat ? BorderFactory.createEmptyBorder(0, 0, 6, 0)
                : BorderFactory.createEmptyBorder(6, 12, 6, 12));
        titleText.setFont(new Font(Font.SANS_SERIF, Font.BOLD, flat ? 18 : 14));
        titleText.setForeground(flat ? text : t.textActive());
        titleText.setBorder(flat ? BorderFactory.createEmptyBorder(10, 0, 0, 0) : null);

        formPanel.setOpaque(!flat);
        formPanel.setBackground(t.base());
        for (JLabel label : formLabels) {
            label.setFont(new Font(Font.SANS_SERIF, flat ? Font.PLAIN : Font.BOLD, flat ? 13 : 12));
            label.setForeground(flat ? t.textInactive() : text);
        }
        Font fieldFont = flat ? new Font(Font.SANS_SERIF, Font.PLAIN, 14) : new Font(Font.MONOSPACED, Font.PLAIN, 13);
        userField.setFont(fieldFont);
        passField.setFont(fieldFont);

        statusPlate.setOpaque(!flat);
        statusPlate.setBackground(new Color(0xE8, 0xE2, 0xDD));
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, flat ? 12 : 11));
        statusLabel.setForeground(statusColor(statusKind));

        buttonRow.setOpaque(!flat);
        buttonRow.setBackground(t.base());
        buttonRow.setLayout(new FlowLayout(flat ? FlowLayout.CENTER : FlowLayout.RIGHT, 10, 0));
        loginBtn.setText(flat ? "Log In" : "  Log In  ");
        for (JButton b : new JButton[]{clearBtn, loginBtn, rebootBtn, poweroffBtn}) {
            b.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            b.setFocusPainted(false);
            if (flat) {
                // The look's own colours: the accent for the default button, flat faces for the others
                b.setBackground(null);
                b.setForeground(null);
            } else {
                b.setBackground(t.base());
                b.setForeground(t.textActive());
            }
        }
        if (flat) {
            loginBtn.setPreferredSize(null);
            Dimension d = loginBtn.getPreferredSize();
            loginBtn.setPreferredSize(new Dimension(Math.max(d.width, 140), d.height));
        } else {
            loginBtn.setPreferredSize(null);
        }
        rebootBtn.setText(flat ? "Restart..." : "Reboot...");
    }

    private Color statusColor(StatusKind kind) {
        if (horizon) {
            return switch (kind) {
                case NORMAL -> theme.textInactive();
                case OK -> theme.textActive();
                case ERROR -> theme.isDark() ? new Color(0xF0, 0x7A, 0x7A) : new Color(0xC0, 0x30, 0x30);
            };
        }
        return switch (kind) {
            case NORMAL -> new Color(0x33, 0x33, 0x33);
            case OK -> new Color(0x11, 0x44, 0x11);
            case ERROR -> new Color(0x99, 0x11, 0x11);
        };
    }

    private void showStatus(String message, StatusKind kind) {
        statusKind = kind;
        statusLabel.setText(message);
        statusLabel.setForeground(statusColor(kind));
    }

    private void paintBackdrop(Graphics2D g2, int w, int h) {
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        if (!horizon) {
            g2.setPaint(new java.awt.GradientPaint(0, 0, new Color(0x3B, 0x54, 0x6E), 0, h, new Color(0x27, 0x38, 0x4A)));
            g2.fillRect(0, 0, w, h);
            return;
        }
        Image picture = picture(theme.isDark() ? "dark" : "light");
        if (picture != null && picture.getWidth(null) > 0) {
            // Fill the screen, cut what sticks out, as the desktop shows it (mode FILL)
            int pw = picture.getWidth(null), ph = picture.getHeight(null);
            double scale = Math.max(w / (double) pw, h / (double) ph);
            int sw = (int) Math.ceil(pw * scale), sh = (int) Math.ceil(ph * scale);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(picture, (w - sw) / 2, (h - sh) / 2, sw, sh, null);
        } else {
            Color accent = theme.activeTitleBg();
            Color top = theme.isDark() ? new Color(0x1A, 0x1B, 0x26) : new Color(0xDD, 0xE3, 0xEE);
            Color bottom = mix(top, accent, theme.isDark() ? 0.35 : 0.45);
            g2.setPaint(new java.awt.GradientPaint(0, 0, top, w, h, bottom));
            g2.fillRect(0, 0, w, h);
        }
    }

    private Image picture(String scheme) {
        return pictures.computeIfAbsent(scheme, s -> {
            File file = new File(HORIZON_PICTURES + s + ".png");
            try {
                return file.isFile() ? ImageIO.read(file) : null;
            } catch (Exception e) {
                return null;
            }
        });
    }

    private void paintMotifCard(Graphics g, JComponent c) {
        g.setColor(c.getBackground());
        g.fillRect(0, 0, c.getWidth(), c.getHeight());
        DesktopTheme t = theme;
        int w = c.getWidth();
        int h = c.getHeight();
        // Outer 3D bevel (raised)
        g.setColor(t.highlight());
        g.drawLine(0, 0, w - 1, 0);
        g.drawLine(0, 0, 0, h - 1);
        g.setColor(t.softHighlight());
        g.drawLine(1, 1, w - 2, 1);
        g.drawLine(1, 1, 1, h - 2);
        g.setColor(t.darkShadow());
        g.drawLine(w - 1, 0, w - 1, h - 1);
        g.drawLine(0, h - 1, w - 1, h - 1);
        g.setColor(t.shadow());
        g.drawLine(w - 2, 1, w - 2, h - 2);
        g.drawLine(1, h - 2, w - 2, h - 2);
    }

    /** Horizon's card: rounded, a soft shadow below it, the window colour slightly see-through. */
    private void paintHorizonCard(Graphics2D g, JComponent c) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = c.getWidth(), h = c.getHeight();
        int inset = 8;
        for (int i = 0; i < inset; i++) {
            g2.setColor(new Color(0, 0, 0, 6 + i * 2));
            g2.fill(new RoundRectangle2D.Float(i, i + 3, w - 2f * i, h - 2f * i - 3, CARD_ARC + inset - i, CARD_ARC + inset - i));
        }
        RoundRectangle2D card = new RoundRectangle2D.Float(inset, inset, w - 2f * inset, h - 2f * inset, CARD_ARC, CARD_ARC);
        Color base = theme.base();
        g2.setComposite(AlphaComposite.SrcOver);
        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 238));
        g2.fill(card);
        g2.setColor(HorizonUI.palette().line());
        g2.setStroke(new BasicStroke(1f));
        g2.draw(card);
        g2.dispose();
    }

    static Color mix(Color a, Color b, double weightOfB) {
        return new Color(
                (int) Math.round(a.getRed() * (1 - weightOfB) + b.getRed() * weightOfB),
                (int) Math.round(a.getGreen() * (1 - weightOfB) + b.getGreen() * weightOfB),
                (int) Math.round(a.getBlue() * (1 - weightOfB) + b.getBlue() * weightOfB));
    }

    /** The home of a user, if the system knows the user. */
    private static Optional<Path> userHome(String username) {
        if (username.isEmpty()) return Optional.empty();
        try {
            return UserAccount.lookup(username).map(account -> Path.of(account.home()));
        } catch (RuntimeException | LinkageError e) {
            // no libc lookup (e.g. preview on another system)
            return Optional.empty();
        }
    }

    /** The first letter of a user name, upper case, for Horizon's round picture; "?" for none. */
    static String initial(String username) {
        String name = username == null ? "" : username.strip();
        return name.isEmpty() ? "?" : name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
    }

    /** Horizon's round user picture: the first letter of the user name on the accent colour. */
    private final class Avatar extends JComponent {
        private static final int SIZE = 76;

        Avatar() {
            setPreferredSize(new Dimension(SIZE, SIZE));
            setMaximumSize(new Dimension(SIZE, SIZE));
            setMinimumSize(new Dimension(SIZE, SIZE));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int d = Math.min(getWidth(), getHeight()) - 2;
            int x = (getWidth() - d) / 2, y = (getHeight() - d) / 2;
            HorizonUI.Palette p = HorizonUI.Palette.of(theme);
            g2.setColor(p.accent());
            g2.fillOval(x, y, d, d);
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, d / 2));
            FontMetrics fm = g2.getFontMetrics();
            String letter = initial(userField == null ? "" : userField.getText());
            g2.setColor(p.onAccent());
            g2.drawString(letter, x + (d - fm.stringWidth(letter)) / 2, y + (d - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    private void performLogin() {
        String username = userField.getText().trim();
        String password = new String(passField.getPassword());
        String sessionType = (String) sessionCombo.getSelectedItem();
        Language language = (Language) languageCombo.getSelectedItem();
        String timeZone = (String) timeZoneCombo.getSelectedItem();

        if (username.isEmpty()) {
            setStatus("Please enter a username.", true);
            userField.requestFocusInWindow();
            return;
        }

        setStatus("Authenticating with system PAM...", false);
        loginBtn.setEnabled(false);

        // Execute authentication on background thread so UI remains responsive
        new Thread(() -> {
            try {
                loginCallback.onLogin(username, password, sessionType, language, timeZone);
            } finally {
                SwingUtilities.invokeLater(() -> loginBtn.setEnabled(true));
            }
        }, "amadeOS-Auth-Thread").start();
    }

    public void setStatus(String message, boolean error) {
        SwingUtilities.invokeLater(() -> {
            showStatus(message, error ? StatusKind.ERROR : StatusKind.OK);
            if (error) {
                passField.selectAll();
                passField.requestFocusInWindow();
            }
        });
    }

    /** Selects the language the user had last time, or the system default for unknown users. */
    private void preselectLanguage() {
        String username = userField.getText().trim();
        Optional<Language> users = Optional.empty();
        if (!username.isEmpty()) {
            try {
                users = UserAccount.lookup(username).flatMap(account -> LoginLanguage.ofUser(Path.of(account.home())));
            } catch (RuntimeException | LinkageError e) {
                // no libc lookup (e.g. preview on another system): fall back to the default
            }
        }
        languageCombo.setSelectedItem(users.orElseGet(() -> LoginLanguage.systemDefault(System.getenv())));
    }

    /** Selects the zone the system has now (an alias such as {@code Etc/UTC} is added to the list). */
    private void preselectTimeZone() {
        String zone = SystemTimeZone.SYSTEM.current().orElseGet(() -> ZoneId.systemDefault().getId());
        boolean listed = false;
        for (int i = 0; i < timeZoneCombo.getItemCount() && !listed; i++) {
            listed = zone.equals(timeZoneCombo.getItemAt(i));
        }
        if (!listed) {
            timeZoneCombo.insertItemAt(zone, 0);
        }
        timeZoneCombo.setSelectedItem(zone);
    }

    /** {@code Europe/Berlin} as "Europe/Berlin  (UTC+02:00)", with the offset of today. */
    static String describeTimeZone(String zone) {
        String name = zone.replace('_', ' ');
        try {
            ZoneOffset offset = ZoneId.of(zone).getRules().getOffset(Instant.now());
            return name + "  (UTC" + (offset.getTotalSeconds() == 0 ? "" : offset.getId()) + ")";
        } catch (DateTimeException e) {
            // The JDK's time zone data may not know a zone the system has.
            return name;
        }
    }

    /**
     * Covers the whole X screen, which may have changed since the greeter was last shown (the display manager
     * sets the user's monitor layout, a session may have changed it), and puts the login screen on the given
     * part of it, the primary monitor; null puts it across the whole screen.
     */
    public void showOn(Rectangle area) {
        loginArea = area == null ? null : new Rectangle(area);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        if (loginArea != null) {
            // AWT may not have seen a new size of the screen yet; it is at least as large as the monitor
            screenSize = new Dimension(Math.max(screenSize.width, loginArea.x + loginArea.width),
                    Math.max(screenSize.height, loginArea.y + loginArea.height));
        }
        setBounds(0, 0, screenSize.width, screenSize.height);
        getContentPane().revalidate();
        getContentPane().doLayout();
        repaint();
    }

    public void resetFields() {
        SwingUtilities.invokeLater(() -> {
            preselectLanguage();
            preselectTimeZone();
            passField.setText("");
            showStatus(PROMPT, StatusKind.NORMAL);
            passField.requestFocusInWindow();
        });
    }

    private void confirmPowerAction(String action, String message) {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                message,
                "Workstation Power Control",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION && powerActionCallback != null) {
            powerActionCallback.accept(action);
        }
    }

    /** Switches to the chosen layout; the computer's own layout keeps its variant (nodeadkeys, ...). */
    private void applySelectedKeyboardLayout() {
        if (!(keyboardCombo.getSelectedItem() instanceof KeyboardLayouts.Layout layout)) {
            return;
        }
        String variant = layout.code().equals(systemLayout.code()) ? systemLayout.variant() : "";
        Thread.ofVirtual().name("amadeOS-Setxkbmap").start(() -> KeyboardLayouts.apply(layout.code(), variant));
    }
}
