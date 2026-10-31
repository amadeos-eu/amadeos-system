package eu.amadeos.system.settings;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.system.users.Account;
import eu.amadeos.system.users.LoginNames;
import eu.amadeos.system.users.UserGroup;
import eu.amadeos.system.users.UserService;
import eu.amadeos.system.users.UserService.UserException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Users and Groups: the people's accounts of the computer and the groups that give them permissions. The Users
 * tab adds and removes accounts and changes the full name, the account type (standard or administrator), the
 * password, whether the account can log in and its groups; the Groups tab shows each permission group with its
 * members and makes and removes groups for sharing folders. Changes go through {@link UserService}: polkit asks
 * for an administrator's password once and keeps it for a few minutes. Everything acts at once.
 */
final class UsersPage extends SettingsPage {
    /** The colours of the pictures of the accounts, chosen by user ID. */
    private static final Color[] PICTURE_COLORS = {new Color(0x3F, 0x6E, 0xA8), new Color(0x4A, 0x8C, 0x5A),
            new Color(0xA8, 0x5A, 0x3F), new Color(0x7A, 0x52, 0x9C), new Color(0x2E, 0x8A, 0x8A),
            new Color(0xA0, 0x7A, 0x2E)};

    private final UserService users;
    private final JTabbedPane tabs = new JTabbedPane();

    private final DefaultListModel<Account> accountModel = new DefaultListModel<>();
    private final JList<Account> accountList = new JList<>(accountModel);
    private final JButton addUser = new JButton(SettingsTexts.get("users.add"));
    private final JButton removeUser = new JButton(SettingsTexts.get("users.remove"));
    private final JLabel picture = new JLabel();
    private final JLabel title = new JLabel(" ");
    private final JLabel subtitle = new JLabel(" ");
    private final JLabel fullName = new JLabel(" ");
    private final JButton changeName = new JButton(SettingsTexts.get("users.change"));
    private final JComboBox<String> type = new JComboBox<>(new String[]{
            SettingsTexts.get("users.type.standard"), SettingsTexts.get("users.type.admin")});
    private final JLabel password = new JLabel("●●●●●●●●");
    private final JButton changePassword = new JButton(SettingsTexts.get("users.change"));
    private final JCheckBox disabled = new JCheckBox(SettingsTexts.get("users.disabled"));
    /** The groups of the account in one line, cut to the width there is (all of them in the tooltip). */
    private final JLabel memberOf = new JLabel(" ") {
        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.min(d.width, 260), d.height);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(60, super.getMinimumSize().height);
        }
    };
    private final JButton chooseGroups = new JButton(SettingsTexts.get("users.groups.choose"));
    private final JLabel home = new JLabel(" ");
    private final JLabel session = new JLabel(" ");
    private final JPanel details = new JPanel(new GridBagLayout());

    private final DefaultListModel<UserGroup> groupModel = new DefaultListModel<>();
    private final JList<UserGroup> groupList = new JList<>(groupModel);
    private final JButton addGroup = new JButton(SettingsTexts.get("users.group.add"));
    private final JButton removeGroup = new JButton(SettingsTexts.get("users.group.remove"));
    private final JLabel groupTitle = new JLabel(" ");
    private final JLabel groupDescription = new JLabel(" ");
    private final JPanel members = new JPanel();

    private List<Account> accounts = List.of();
    private List<UserGroup> groups = List.of();
    private boolean updating;
    private boolean busy;

    UsersPage(Consumer<String> status, UserService users) {
        super(SettingsCategory.USERS, status);
        this.users = users;

        accountList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        accountList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected,
                                                          boolean focus) {
                Account a = (Account) value;
                super.getListCellRendererComponent(l, describe(a, users.self()), index, selected, focus);
                setIcon(picture(a, 30));
                setIconTextGap(8);
                setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
                return this;
            }
        });
        accountList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showAccount();
        });
        addUser.addActionListener(e -> askAdd());
        removeUser.addActionListener(e -> withAccount(this::askRemove));
        changeName.addActionListener(e -> withAccount(this::askName));
        type.addActionListener(e -> {
            if (updating) return;
            withAccount(a -> {
                boolean admin = type.getSelectedIndex() == 1;
                if (admin != a.admin()) act(() -> users.setAdmin(a.login(), admin), SettingsTexts.format(
                        admin ? "users.status.admin" : "users.status.standard", a.displayName()));
            });
        });
        changePassword.addActionListener(e -> withAccount(this::askPassword));
        disabled.addActionListener(e -> {
            if (updating) return;
            withAccount(a -> {
                boolean off = disabled.isSelected();
                act(() -> users.setDisabled(a.login(), off), SettingsTexts.format(
                        off ? "users.status.disabled" : "users.status.enabled", a.displayName()));
            });
        });
        chooseGroups.addActionListener(e -> withAccount(this::askGroups));
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() * 1.25f));

        groupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        groupList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected,
                                                          boolean focus) {
                super.getListCellRendererComponent(l, describe((UserGroup) value), index, selected, focus);
                setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
                return this;
            }
        });
        groupList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showGroup();
        });
        addGroup.addActionListener(e -> askAddGroup());
        removeGroup.addActionListener(e -> withGroup(this::askRemoveGroup));
        groupTitle.setFont(groupTitle.getFont().deriveFont(Font.BOLD));
        members.setLayout(new BoxLayout(members, BoxLayout.Y_AXIS));

        tabs.addTab(SettingsTexts.get("users.tab.users"), usersTab());
        tabs.addTab(SettingsTexts.get("users.tab.groups"), groupsTab());
        layoutPage(tabs);
        showAccount();
        showGroup();
        refresh();
    }

    private JComponent usersTab() {
        JScrollPane scroll = new JScrollPane(accountList);
        scroll.setPreferredSize(new Dimension(250, 300));
        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.add(scroll, BorderLayout.CENTER);
        left.add(line(addUser, removeUser), BorderLayout.SOUTH);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 6, 3, 6);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        c.gridheight = 2;
        details.add(picture, c);
        c.gridheight = 1;
        c.gridx = 1;
        c.gridwidth = 2;
        details.add(title, c);
        c.gridy = 1;
        details.add(subtitle, c);
        c.gridwidth = 1;
        row(2, "users.fullname", fullName, changeName);
        row(3, "users.type", line0(type), null);
        row(4, "users.password", password, changePassword);
        row(5, "users.groups", memberOf, chooseGroups);
        row(6, "users.home", home, null);
        row(7, "users.session", session, null);
        c.gridx = 1;
        c.gridy = 8;
        c.gridwidth = 2;
        details.add(disabled, c);
        c.gridy = 9;
        c.weighty = 1;
        details.add(Box.createGlue(), c);
        details.setBorder(BorderFactory.createTitledBorder(SettingsTexts.get("users.details")));

        JPanel tab = new JPanel(new BorderLayout(10, 8));
        tab.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        tab.add(left, BorderLayout.WEST);
        tab.add(details, BorderLayout.CENTER);
        tab.add(note(SettingsTexts.get("users.hint")), BorderLayout.SOUTH);
        return tab;
    }

    private void row(int y, String label, JComponent value, JComponent button) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 6, 3, 6);
        c.anchor = GridBagConstraints.WEST;
        c.gridy = y;
        c.gridx = 0;
        details.add(new JLabel(SettingsTexts.get(label)), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        details.add(value, c);
        if (button != null) {
            c.gridx = 2;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            details.add(button, c);
        }
    }

    /** A component at its own width, on the left of its cell. */
    private static JComponent line0(JComponent c) {
        JPanel p = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        p.add(c);
        return p;
    }

    private JComponent groupsTab() {
        JScrollPane scroll = new JScrollPane(groupList);
        scroll.setPreferredSize(new Dimension(300, 300));
        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.add(scroll, BorderLayout.CENTER);
        left.add(line(addGroup, removeGroup), BorderLayout.SOUTH);

        JPanel right = new JPanel(new BorderLayout(0, 6));
        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.add(groupTitle);
        head.add(Box.createVerticalStrut(4));
        head.add(groupDescription);
        right.add(head, BorderLayout.NORTH);
        JPanel top = new JPanel(new BorderLayout());
        top.add(members, BorderLayout.NORTH);
        JScrollPane memberScroll = new JScrollPane(top);
        memberScroll.setBorder(BorderFactory.createTitledBorder(SettingsTexts.get("users.group.members")));
        right.add(memberScroll, BorderLayout.CENTER);
        right.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));

        JPanel tab = new JPanel(new BorderLayout(10, 8));
        tab.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        tab.add(left, BorderLayout.WEST);
        tab.add(right, BorderLayout.CENTER);
        tab.add(note(SettingsTexts.get("users.groups.hint")), BorderLayout.SOUTH);
        return tab;
    }

    // --- texts -------------------------------------------------------------------------------------------

    /** "<b>Anna Müller</b><br>anna · Administrator · you". */
    static String describe(Account a, String self) {
        List<String> second = new ArrayList<>();
        second.add(a.login());
        second.add(SettingsTexts.get(a.admin() ? "users.type.admin" : "users.type.standard"));
        if (a.login().equals(self)) second.add(SettingsTexts.get("users.you"));
        else if (a.loggedIn()) second.add(SettingsTexts.get("users.logged-in"));
        if (a.disabled()) second.add(SettingsTexts.get("users.is-disabled"));
        return "<html><b>" + escape(a.displayName()) + "</b><br>" + escape(String.join(" · ", second)) + "</html>";
    }

    /** "<b>Manage printers</b><br>lpadmin · 2 members". */
    static String describe(UserGroup g) {
        return "<html><b>" + escape(groupLabel(g)) + "</b><br>" + escape(g.name() + " · "
                + SettingsTexts.format("users.group.count", g.members().size())) + "</html>";
    }

    /** What a group is for: "Administer the computer" for sudo, the name for a group made on the computer. */
    static String groupLabel(UserGroup g) {
        return g.kind() == UserGroup.Kind.PERMISSION ? SettingsTexts.get("users.group." + g.name()) : g.name();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;");
    }

    /** A round picture with the first letter of the name, coloured by user ID. */
    static Icon picture(Account a, int size) {
        Color color = PICTURE_COLORS[Math.floorMod(a.uid(), PICTURE_COLORS.length)];
        String letter = a.initial();
        boolean off = a.disabled();
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(off ? Color.GRAY : color);
                g2.fillOval(x, y, size - 1, size - 1);
                g2.setColor(color.darker());
                g2.setStroke(new BasicStroke(1f));
                g2.drawOval(x, y, size - 1, size - 1);
                g2.setColor(Color.WHITE);
                g2.setFont(c.getFont().deriveFont(Font.BOLD, size * 0.5f));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(letter, x + (size - fm.stringWidth(letter)) / 2f,
                        y + (size - fm.getHeight()) / 2f + fm.getAscent());
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    // --- state -------------------------------------------------------------------------------------------

    /** Reads the accounts and groups again in the background. */
    void refresh() {
        new SwingWorker<Object[], Void>() {
            private String error;

            @Override
            protected Object[] doInBackground() {
                try {
                    return new Object[]{users.accounts(), users.groups()};
                } catch (UserException e) {
                    error = e.getMessage();
                    return new Object[]{List.of(), List.of()};
                }
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void done() {
                try {
                    Object[] result = get();
                    show((List<Account>) result[0], (List<UserGroup>) result[1]);
                    if (error != null) status(SettingsTexts.format("users.status.failed", error));
                } catch (Exception e) {
                    status(SettingsTexts.format("users.status.failed", e.getMessage()));
                }
            }
        }.execute();
    }

    void show(List<Account> accounts, List<UserGroup> groups) {
        this.accounts = accounts;
        this.groups = groups;
        String selectedLogin = accountList.getSelectedValue() == null ? users.self()
                : accountList.getSelectedValue().login();
        String selectedGroup = groupList.getSelectedValue() == null ? null : groupList.getSelectedValue().name();
        accountModel.clear();
        accounts.forEach(accountModel::addElement);
        groupModel.clear();
        groups.forEach(groupModel::addElement);
        int a = indexOf(accounts, selectedLogin);
        accountList.setSelectedIndex(a >= 0 ? a : accounts.isEmpty() ? -1 : 0);
        int g = -1;
        for (int i = 0; i < groups.size(); i++) if (groups.get(i).name().equals(selectedGroup)) g = i;
        groupList.setSelectedIndex(g >= 0 ? g : groups.isEmpty() ? -1 : 0);
        showAccount();
        showGroup();
    }

    private static int indexOf(List<Account> accounts, String login) {
        for (int i = 0; i < accounts.size(); i++) if (accounts.get(i).login().equals(login)) return i;
        return -1;
    }

    List<Account> shownAccounts() {
        return accounts;
    }

    JTabbedPane tabs() {
        return tabs;
    }

    JList<Account> accountList() {
        return accountList;
    }

    JComboBox<String> accountType() {
        return type;
    }

    JCheckBox disabledBox() {
        return disabled;
    }

    JButton removeButton() {
        return removeUser;
    }

    JPanel memberBoxes() {
        return members;
    }

    void select(String login) {
        accountList.setSelectedIndex(indexOf(accounts, login));
    }

    void selectGroup(String name) {
        for (int i = 0; i < groups.size(); i++) if (groups.get(i).name().equals(name)) groupList.setSelectedIndex(i);
    }

    private void showAccount() {
        Account a = accountList.getSelectedValue();
        updating = true;
        try {
            boolean any = a != null;
            boolean self = any && a.login().equals(users.self());
            picture.setIcon(any ? picture(a, 56) : null);
            title.setText(any ? a.displayName() : " ");
            subtitle.setText(any ? a.login() + (self ? " · " + SettingsTexts.get("users.you") : "") : " ");
            fullName.setText(any ? (a.fullName().isBlank() ? "–" : a.fullName()) : " ");
            type.setSelectedIndex(any && a.admin() ? 1 : 0);
            disabled.setSelected(any && a.disabled());
            memberOf.setText(any ? memberText(a) : " ");
            memberOf.setToolTipText(any ? "<html>" + escape(memberText(a)).replace(", ", "<br>") + "</html>" : null);
            home.setText(any ? a.home() : " ");
            session.setText(!any ? " " : SettingsTexts.get(a.loggedIn() || self ? "users.session.on" : "users.session.off"));
            for (JComponent c : List.of(changeName, type, changePassword, chooseGroups)) c.setEnabled(any && !busy);
            // You cannot lock yourself out or remove yourself; the helper refuses it as well.
            disabled.setEnabled(any && !self && !busy);
            removeUser.setEnabled(any && !self && !busy);
            removeUser.setToolTipText(self ? SettingsTexts.get("users.remove.self") : null);
            addUser.setEnabled(!busy);
        } finally {
            updating = false;
        }
    }

    /** "7: Administer the computer, Manage printers, …": the groups by what they give. */
    private String memberText(Account a) {
        List<String> names = new ArrayList<>();
        for (UserGroup g : groups) if (a.groups().contains(g.name())) names.add(groupLabel(g));
        if (names.isEmpty()) return SettingsTexts.get("users.groups.none");
        return SettingsTexts.format("users.groups.summary", names.size(), String.join(", ", names));
    }

    private void showGroup() {
        UserGroup g = groupList.getSelectedValue();
        members.removeAll();
        if (g == null) {
            groupTitle.setText(" ");
            groupDescription.setText(" ");
        } else {
            groupTitle.setText(groupLabel(g) + "  (" + g.name() + ")");
            groupDescription.setText("<html>" + escape(g.kind() == UserGroup.Kind.PERMISSION
                    ? SettingsTexts.get("users.group." + g.name() + ".description")
                    : SettingsTexts.get("users.group.own.description")) + "</html>");
            for (Account a : accounts) {
                JCheckBox box = new JCheckBox(a.displayName() + " (" + a.login() + ")", g.has(a.login()));
                box.setEnabled(!busy);
                box.addActionListener(e -> {
                    boolean join = box.isSelected();
                    act(() -> {
                        if (join) users.join(a.login(), g.name());
                        else users.leave(a.login(), g.name());
                    }, SettingsTexts.format(join ? "users.status.joined" : "users.status.left", a.displayName(),
                            groupLabel(g)));
                });
                members.add(box);
            }
        }
        removeGroup.setEnabled(g != null && g.kind() == UserGroup.Kind.OWN && !busy);
        addGroup.setEnabled(!busy);
        members.revalidate();
        members.repaint();
    }

    private void withAccount(Consumer<Account> action) {
        Account a = accountList.getSelectedValue();
        if (a != null) action.accept(a);
    }

    private void withGroup(Consumer<UserGroup> action) {
        UserGroup g = groupList.getSelectedValue();
        if (g != null) action.accept(g);
    }

    // --- changes -----------------------------------------------------------------------------------------

    @FunctionalInterface
    interface Change {
        void run() throws UserException;
    }

    /** Runs a change in the background, then reads the accounts again; the outcome goes to the status line. */
    void act(Change change, String success) {
        busy = true;
        showAccount();
        showGroup();
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws UserException {
                change.run();
                return null;
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    get();
                    status(success);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (java.util.concurrent.ExecutionException e) {
                    status(e.getCause() instanceof UserException u ? message(u)
                            : SettingsTexts.format("users.status.failed", e.getCause().getMessage()));
                }
                refresh();
            }
        }.execute();
    }

    /** The status line for a change that was not made. */
    static String message(UserException e) {
        return switch (e.reason()) {
            case DISMISSED -> SettingsTexts.get("users.status.dismissed");
            case NOT_AUTHORIZED -> SettingsTexts.get("users.status.denied");
            case WRONG_PASSWORD -> e.code().equals("wrong-current") ? SettingsTexts.get("users.status.password.wrong")
                    : SettingsTexts.format("users.status.password", e.getMessage());
            case REFUSED -> switch (e.code()) {
                case "self", "last-admin", "logged-in", "exists" ->
                        SettingsTexts.format("users.status.refused." + e.code(), e.getMessage());
                default -> SettingsTexts.format("users.status.refused", e.getMessage());
            };
            case FAILED -> SettingsTexts.format("users.status.failed", e.getMessage());
        };
    }

    void askAdd() {
        JTextField name = new JTextField(22);
        JTextField login = new JTextField(14);
        JComboBox<String> kind = new JComboBox<>(new String[]{
                SettingsTexts.get("users.type.standard"), SettingsTexts.get("users.type.admin")});
        JPasswordField first = new JPasswordField(16);
        JPasswordField second = new JPasswordField(16);
        List<String> taken = accounts.stream().map(Account::login).toList();
        boolean[] typed = {false};
        name.getDocument().addDocumentListener(onChange(() -> {
            if (!typed[0]) login.setText(LoginNames.suggest(name.getText(), taken));
        }));
        login.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyTyped(java.awt.event.KeyEvent e) {
                typed[0] = true;
            }
        });
        JPanel form = form(new String[]{"users.fullname", "users.login", "users.type", "users.password.new",
                "users.password.repeat"}, name, login, kind, first, second);
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(form, BorderLayout.CENTER);
        panel.add(hint(SettingsTexts.get("users.add.hint")), BorderLayout.SOUTH);
        while (true) {
            if (JOptionPane.showConfirmDialog(this, panel, SettingsTexts.get("users.add.title"),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
                clear(first, second);
                return;
            }
            String problem = addProblem(name.getText().strip(), login.getText().strip(), taken, first.getPassword(),
                    second.getPassword());
            if (problem == null) break;
            JOptionPane.showMessageDialog(this, problem, SettingsTexts.get("users.add.title"),
                    JOptionPane.WARNING_MESSAGE);
        }
        String full = name.getText().strip();
        String user = login.getText().strip();
        boolean admin = kind.getSelectedIndex() == 1;
        char[] secret = first.getPassword();
        clear(first, second);
        act(() -> {
            try {
                users.add(user, full, admin, secret);
            } finally {
                Arrays.fill(secret, '\0');
            }
        }, SettingsTexts.format("users.status.added", full.isEmpty() ? user : full));
    }

    /** What is wrong with a new account, or null. */
    static String addProblem(String fullName, String login, List<String> taken, char[] first, char[] second) {
        try {
            if (!LoginNames.validFullName(fullName)) return SettingsTexts.get("users.problem.fullname");
            if (!LoginNames.valid(login)) return SettingsTexts.get("users.problem.login");
            if (taken.contains(login)) return SettingsTexts.format("users.problem.taken", login);
            return passwordProblem(first, second);
        } finally {
            Arrays.fill(first, '\0');
            Arrays.fill(second, '\0');
        }
    }

    static String passwordProblem(char[] first, char[] second) {
        if (first.length == 0) return SettingsTexts.get("users.problem.empty");
        if (!Arrays.equals(first, second)) return SettingsTexts.get("users.problem.mismatch");
        for (char ch : first) if (ch == '\n' || ch == '\r') return SettingsTexts.get("users.problem.empty");
        return null;
    }

    void askRemove(Account a) {
        JRadioButton keep = new JRadioButton(SettingsTexts.format("users.remove.keep", a.home()), true);
        JRadioButton delete = new JRadioButton(SettingsTexts.format("users.remove.delete", a.home()));
        ButtonGroup choice = new ButtonGroup();
        choice.add(keep);
        choice.add(delete);
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new JLabel(SettingsTexts.format("users.remove.question", a.displayName(), a.login())));
        panel.add(Box.createVerticalStrut(8));
        panel.add(keep);
        panel.add(delete);
        if (JOptionPane.showConfirmDialog(this, panel, SettingsTexts.get("users.remove.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        boolean deleteHome = delete.isSelected();
        act(() -> users.remove(a.login(), deleteHome), SettingsTexts.format("users.status.removed", a.displayName()));
    }

    private void askName(Account a) {
        Object value = JOptionPane.showInputDialog(this, SettingsTexts.format("users.fullname.prompt", a.login()),
                SettingsTexts.get("users.fullname.title"), JOptionPane.PLAIN_MESSAGE, null, null, a.fullName());
        if (value == null) return;
        String name = value.toString().strip();
        if (name.equals(a.fullName())) return;
        if (!LoginNames.validFullName(name)) {
            status(SettingsTexts.get("users.problem.fullname"));
            return;
        }
        act(() -> users.setFullName(a.login(), name), SettingsTexts.format("users.status.name", a.login(), name));
    }

    void askPassword(Account a) {
        boolean self = a.login().equals(users.self());
        JPasswordField current = new JPasswordField(16);
        JPasswordField first = new JPasswordField(16);
        JPasswordField second = new JPasswordField(16);
        JPanel form = self
                ? form(new String[]{"users.password.current", "users.password.new", "users.password.repeat"},
                current, first, second)
                : form(new String[]{"users.password.new", "users.password.repeat"}, first, second);
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel(SettingsTexts.format("users.password.prompt", a.displayName())), BorderLayout.NORTH);
        panel.add(form, BorderLayout.CENTER);
        panel.add(hint(SettingsTexts.get(self ? "users.password.self.hint" : "users.password.other.hint")),
                BorderLayout.SOUTH);
        if (JOptionPane.showConfirmDialog(this, panel, SettingsTexts.get("users.password.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            clear(current, first, second);
            return;
        }
        char[] neu = first.getPassword();
        char[] again = second.getPassword();
        String problem = passwordProblem(neu, again);
        Arrays.fill(again, '\0');
        if (problem != null) {
            Arrays.fill(neu, '\0');
            clear(current, first, second);
            status(problem);
            return;
        }
        char[] old = current.getPassword();
        clear(current, first, second);
        act(() -> {
            try {
                if (self) users.changeOwnPassword(old, neu);
                else users.setPassword(a.login(), neu);
            } finally {
                Arrays.fill(old, '\0');
                Arrays.fill(neu, '\0');
            }
        }, SettingsTexts.format("users.status.password.changed", a.displayName()));
    }

    /** A list of checkboxes, one per group, for one account; joins and leaves the changed ones. */
    void askGroups(Account a) {
        Map<UserGroup, JCheckBox> boxes = new LinkedHashMap<>();
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        for (UserGroup g : groups) {
            String raw = g.kind() == UserGroup.Kind.PERMISSION ? " <font color=gray>(" + g.name() + ")</font>" : "";
            JCheckBox box = new JCheckBox("<html>" + escape(groupLabel(g)) + raw + "</html>",
                    a.groups().contains(g.name()));
            boxes.put(g, box);
            list.add(box);
        }
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(380, Math.min(360, 26 * Math.max(4, groups.size()) + 8)));
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel(SettingsTexts.format("users.groups.prompt", a.displayName())), BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        if (JOptionPane.showConfirmDialog(this, panel, SettingsTexts.get("users.groups.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        List<String> join = new ArrayList<>();
        List<String> leave = new ArrayList<>();
        boxes.forEach((g, box) -> {
            boolean was = a.groups().contains(g.name());
            if (box.isSelected() && !was) join.add(g.name());
            if (!box.isSelected() && was) leave.add(g.name());
        });
        if (join.isEmpty() && leave.isEmpty()) return;
        act(() -> {
            for (String g : join) users.join(a.login(), g);
            for (String g : leave) users.leave(a.login(), g);
        }, SettingsTexts.format("users.status.groups", a.displayName()));
    }

    private void askAddGroup() {
        Object value = JOptionPane.showInputDialog(this, SettingsTexts.get("users.group.add.prompt"),
                SettingsTexts.get("users.group.add.title"), JOptionPane.PLAIN_MESSAGE, null, null, "");
        if (value == null) return;
        String name = value.toString().strip();
        if (!LoginNames.valid(name)) {
            status(SettingsTexts.get("users.problem.group"));
            return;
        }
        act(() -> users.addGroup(name), SettingsTexts.format("users.status.group.added", name));
    }

    private void askRemoveGroup(UserGroup g) {
        if (JOptionPane.showConfirmDialog(this, SettingsTexts.format("users.group.remove.question", g.name()),
                SettingsTexts.get("users.group.remove.title"), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        act(() -> users.removeGroup(g.name()), SettingsTexts.format("users.status.group.removed", g.name()));
    }

    private static JPanel form(String[] labels, JComponent... fields) {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < labels.length; i++) {
            c.gridy = i;
            c.gridx = 0;
            form.add(new JLabel(SettingsTexts.get(labels[i])), c);
            c.gridx = 1;
            form.add(fields[i], c);
        }
        return form;
    }

    /** A hint in a dialog, wrapped at a fixed width so that the dialog stays narrow. */
    private static JComponent hint(String text) {
        JLabel label = new JLabel("<html><div style='width:" + Math.round(AmadeUI.scaled(330f))
                + "px'>" + escape(text) + "</div></html>");
        label.setFont(label.getFont().deriveFont(AmadeUI.scaled(11f)));
        return label;
    }

    private static void clear(JPasswordField... fields) {
        for (JPasswordField f : fields) f.setText("");
    }

    private static DocumentListener onChange(Runnable r) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                r.run();
            }
        };
    }

    @Override
    protected String apply() {
        refresh();
        return null;
    }
}
