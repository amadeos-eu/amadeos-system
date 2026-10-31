package eu.amadeos.system.notify;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The history of the notifications ({@code amadeos-notifications}, a click on the Front Panel's bell): today,
 * yesterday and earlier, the newest first, new ones with a bar in the title colour. A click (or Enter) on an entry
 * runs its default action, or starts its program once the sender has quit; the × at the right (or Delete) removes
 * it. Do not disturb can be switched here, and Clear All empties the history.
 */
final class HistoryWindow extends JFrame {

    /** What the window does with the history; the daemon's core carries it out. */
    interface Actions {
        List<NotificationHistory.Entry> entries();

        boolean manualDnd();

        void setManualDnd(boolean on);

        void open(long id);

        void remove(long id);

        void clearAll();

        /** The window is up: what it shows counts as seen. */
        void shown();
    }

    private static final int ROW_HEIGHT = 64;
    private static final int HEADER_HEIGHT = 22;

    private final Messages texts;
    private final Actions actions;
    private final Supplier<Instant> now;
    private final ZoneId zone;
    private final DefaultListModel<Object> model = new DefaultListModel<>();
    private final JList<Object> list = new JList<>(model);
    private final JCheckBox dnd;
    private final JLabel footer = new JLabel(" ");
    private final JLabel hint = new JLabel(" ");
    private final JLabel empty;
    private final JPanel center = new JPanel(new BorderLayout());
    private final JScrollPane scroll = new JScrollPane(list);
    private final JButton clear;
    private List<NotificationHistory.Entry> unseenBefore = List.of();

    HistoryWindow(Messages texts, Actions actions, Supplier<Instant> now, ZoneId zone) {
        super(texts.get("title"));
        this.texts = texts;
        this.actions = actions;
        this.now = now;
        this.zone = zone;
        setName("notifications.history");
        setDefaultCloseOperation(HIDE_ON_CLOSE);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index < 0 || !(model.get(index) instanceof NotificationHistory.Entry entry)) return;
                Rectangle cell = list.getCellBounds(index, index);
                if (cell == null || !cell.contains(e.getPoint())) return;
                if (e.getX() > cell.x + cell.width - Math.round(AmadeUI.scaled(30))) {
                    actions.remove(entry.notification().id());
                } else if (e.getClickCount() == 1) {
                    actions.open(entry.notification().id());
                }
            }
        });

        empty = new JLabel(texts.get("empty"), SwingConstants.CENTER);
        empty.setBorder(BorderFactory.createEmptyBorder(40, 16, 40, 16));
        hint.setFont(hint.getFont().deriveFont(AmadeUI.scaled(11f)));
        footer.setFont(footer.getFont().deriveFont(AmadeUI.scaled(11f)));

        dnd = Mnemonics.apply(new JCheckBox(), texts.get("dnd"));
        dnd.setName("notifications.dnd");
        dnd.addActionListener(e -> {
            actions.setManualDnd(dnd.isSelected());
            updateHint();
        });
        clear = Mnemonics.apply(new JButton(), texts.get("button.clear"));
        clear.setName("notifications.clear");
        clear.addActionListener(e -> clearAll());
        JButton close = new JButton(texts.get("button.close"));
        close.addActionListener(e -> setVisible(false));

        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        top.add(dnd, BorderLayout.WEST);
        JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        topRight.add(clear);
        top.add(topRight, BorderLayout.EAST);
        top.add(hint, BorderLayout.SOUTH);

        scroll.setPreferredSize(new Dimension(Math.round(AmadeUI.scaled(480)), Math.round(AmadeUI.scaled(ROW_HEIGHT)) * 7));
        scroll.getVerticalScrollBar().setUnitIncrement(Math.round(AmadeUI.scaled(ROW_HEIGHT)) / 2);
        center.setBorder(AmadeUI.sunken());

        JPanel bottom = new JPanel(new BorderLayout(12, 0));
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        bottom.add(footer, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new GridLayout(1, 0, 8, 0));
        buttons.add(close);
        bottom.add(buttons, BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        root.add(top, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close", () -> setVisible(false));
        bind(list, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open", () -> selected(actions::open));
        bind(list, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete", () -> selected(actions::remove));
        getRootPane().setDefaultButton(close);
        refresh();
        pack();
    }

    /** Shows the window in the corner of the bell (bottom right) and marks what it shows as seen. */
    void showWindow() {
        unseenBefore = actions.entries().stream().filter(e -> !e.seen()).toList();
        refresh();
        if (!isVisible()) {
            pack();
            Rectangle screen = getGraphicsConfiguration().getBounds();
            setLocation(screen.x + screen.width - getWidth() - 24,
                    Math.max(screen.y + 24, screen.y + screen.height - getHeight() - Math.round(AmadeUI.scaled(140))));
        }
        setVisible(true);
        setExtendedState(NORMAL);
        toFront();
        list.requestFocusInWindow();
        actions.shown();
    }

    /** Shows the history again; on the event dispatch thread. */
    void refresh() {
        List<NotificationHistory.Entry> entries = actions.entries();
        Object selected = list.getSelectedValue();
        model.clear();
        LocalDate today = LocalDate.ofInstant(now.get(), zone);
        String lastHeader = null;
        for (NotificationHistory.Entry e : entries) {
            LocalDate day = LocalDate.ofInstant(e.notification().time(), zone);
            String header = day.equals(today) ? texts.get("today")
                    : day.equals(today.minusDays(1)) ? texts.get("yesterday") : texts.get("earlier");
            if (!header.equals(lastHeader)) {
                model.addElement(header);
                lastHeader = header;
            }
            model.addElement(e);
        }
        if (selected instanceof NotificationHistory.Entry s) {
            for (int i = 0; i < model.size(); i++) {
                if (model.get(i) instanceof NotificationHistory.Entry e && e.notification().id() == s.notification().id()) {
                    list.setSelectedIndex(i);
                }
            }
        }
        center.removeAll();
        center.add(entries.isEmpty() ? empty : scroll, BorderLayout.CENTER);
        center.revalidate();
        center.repaint();
        long unseen = entries.stream().filter(e -> !e.seen()).count()
                + unseenBefore.stream().filter(u -> entries.stream().anyMatch(
                        e -> e.seen() && e.notification().id() == u.notification().id())).count();
        footer.setText(texts.format("footer", unseen));
        clear.setEnabled(!entries.isEmpty());
        dnd.setSelected(actions.manualDnd());
        updateHint();
    }

    private void updateHint() {
        hint.setText(dnd.isSelected() ? texts.get("dnd.hint") : " ");
    }

    private void clearAll() {
        int n = actions.entries().size();
        if (n == 0) return;
        int answer = JOptionPane.showConfirmDialog(this, texts.format("clear.confirm", n), texts.get("title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer == JOptionPane.OK_OPTION) actions.clearAll();
    }

    private void selected(java.util.function.LongConsumer action) {
        if (list.getSelectedValue() instanceof NotificationHistory.Entry e) action.accept(e.notification().id());
    }

    private boolean wasNew(NotificationHistory.Entry e) {
        return !e.seen() || unseenBefore.stream().anyMatch(u -> u.notification().id() == e.notification().id());
    }

    private static void bind(JComponent component, KeyStroke key, String name, Runnable action) {
        component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key, name);
        component.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    /** A day's heading, or a notification with icon, program, time, summary, one line of the body and an ×. */
    private final class Renderer extends JPanel implements ListCellRenderer<Object> {
        private final JLabel icon = new JLabel();
        private final JLabel meta = new JLabel();
        private final JLabel summary = new JLabel();
        private final JLabel body = new JLabel();
        private final JLabel header = new JLabel();
        private final JPanel text = new JPanel(new GridLayout(3, 1, 0, 0));
        private boolean isHeader;
        private boolean isNew;
        private boolean isSelected;

        Renderer() {
            super(new BorderLayout(10, 0));
            for (JLabel l : List.of(meta, summary, body, header)) l.putClientProperty("html.disable", Boolean.TRUE);
            text.setOpaque(false);
            text.add(meta);
            text.add(summary);
            text.add(body);
        }

        @Override
        public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected,
                                                      boolean focus) {
            removeAll();
            DesktopTheme t = AmadeUI.theme();
            isSelected = selected && value instanceof NotificationHistory.Entry;
            setBackground(isSelected ? t.selectionBackground() : l.getBackground());
            setOpaque(true);
            if (value instanceof String h) {
                isHeader = true;
                isNew = false;
                header.setText(h.toUpperCase(texts.locale()));
                header.setFont(l.getFont().deriveFont(Font.BOLD, AmadeUI.scaled(10f)));
                header.setForeground(t.textInactive());
                setBorder(BorderFactory.createEmptyBorder(4, 10, 2, 10));
                add(header, BorderLayout.CENTER);
                setPreferredSize(new Dimension(10, Math.round(AmadeUI.scaled(HEADER_HEIGHT))));
                return this;
            }
            isHeader = false;
            NotificationHistory.Entry e = (NotificationHistory.Entry) value;
            Notification n = e.notification();
            isNew = wasNew(e);
            java.awt.Color fg = isSelected ? t.selectionText() : l.getForeground();
            icon.setIcon(NotificationLook.icon(n, Math.round(AmadeUI.scaled(32))));
            icon.setVerticalAlignment(SwingConstants.TOP);
            meta.setText(n.appName() + "  ·  " + NotificationLook.clock(n.time(), now.get(), zone, texts));
            meta.setFont(l.getFont().deriveFont(Font.PLAIN, AmadeUI.scaled(11f)));
            meta.setForeground(n.critical() && !isSelected ? NotificationLook.CRITICAL : isSelected ? fg : t.textInactive());
            summary.setText(n.summary());
            summary.setFont(l.getFont().deriveFont(Font.BOLD));
            summary.setForeground(fg);
            body.setText(BodyMarkup.plain(n.body()).replace('\n', ' '));
            body.setFont(l.getFont().deriveFont(Font.PLAIN));
            body.setForeground(fg);
            setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 30));
            add(icon, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
            setToolTipText(BodyMarkup.plain(n.body()));
            setPreferredSize(new Dimension(10, Math.round(AmadeUI.scaled(ROW_HEIGHT))));
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (isHeader) {
                g.setColor(AmadeUI.theme().shadow());
                int y = getHeight() / 2 + 1;
                int x = header.getX() + header.getPreferredSize().width + 10;
                if (x < getWidth() - 10) g.drawLine(x, y, getWidth() - 10, y);
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            if (isNew) {
                g2.setColor(AmadeUI.theme().activeTitleBg());
                g2.fillRect(2, 3, 4, getHeight() - 6);
            }
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isSelected ? AmadeUI.theme().selectionText() : AmadeUI.theme().textInactive());
            g2.setStroke(new BasicStroke(1.5f));
            int s = Math.round(AmadeUI.scaled(8));
            int x = getWidth() - s - 12;
            int y = 8;
            g2.drawLine(x, y, x + s, y + s);
            g2.drawLine(x + s, y, x, y + s);
            g2.setColor(AmadeUI.theme().highlight());
            g2.drawLine(12, getHeight() - 1, getWidth() - 12, getHeight() - 1);
            g2.dispose();
        }
    }

    /** For the pictures of the handbooks and the tests: the rows as they are listed. */
    List<Object> rows() {
        List<Object> out = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) out.add(model.get(i));
        return out;
    }
}
