package eu.amadeos.system.clipboard;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
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
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.ZoneId;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The clipboard history, opened with Ctrl+Alt+V or from the Front Panel's Tools drawer: the entries newest
 * first, each with a line of its text, the names of its files or a thumbnail of its image. Enter or a double
 * click puts the chosen entry on the clipboard and closes the window; 1 to 9 choose an entry directly.
 * Recording can be paused and the history cleared here.
 */
final class HistoryWindow extends JFrame {

    /** What the window does with the history; the daemon carries it out on the manager's worker. */
    interface Actions {
        List<ClipEntry> entries();

        boolean historyOn();

        boolean paused();

        void choose(ClipEntry entry);

        void remove(ClipEntry entry);

        void clear();

        void setPaused(boolean paused);
    }

    private static final int ROW_HEIGHT = EntryPreview.THUMB_HEIGHT + 8;

    private final Messages texts;
    private final Actions actions;
    private final ZoneId zone;
    private final DefaultListModel<ClipEntry> model = new DefaultListModel<>();
    private final JList<ClipEntry> list = new JList<>(model);
    private final JLabel hint = new JLabel(" ");
    private final JLabel empty;
    private final JCheckBox pause;
    private final JButton copy;
    private final JButton delete;
    private final JButton clear;
    private final Map<ClipEntry, Icon> thumbnails = new IdentityHashMap<>();
    private final JPanel center = new JPanel(new BorderLayout());
    private final JScrollPane scroll = new JScrollPane(list);

    HistoryWindow(Messages texts, Actions actions, ZoneId zone) {
        super(texts.get("title"));
        this.texts = texts;
        this.actions = actions;
        this.zone = zone;
        setDefaultCloseOperation(HIDE_ON_CLOSE);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        list.setFixedCellHeight(ROW_HEIGHT);
        list.setVisibleRowCount(8);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && list.locationToIndex(e.getPoint()) >= 0) chooseSelected();
            }
        });
        list.addListSelectionListener(e -> updateButtons());

        empty = new JLabel(texts.get("empty"), SwingConstants.CENTER);
        empty.setBorder(BorderFactory.createEmptyBorder(24, 16, 24, 16));
        hint.setFont(hint.getFont().deriveFont(11f));
        hint.putClientProperty("html.disable", Boolean.TRUE);

        pause = Mnemonics.apply(new JCheckBox(), texts.get("pause"));
        pause.addActionListener(e -> {
            actions.setPaused(pause.isSelected());
            updateHint();
        });
        copy = Mnemonics.apply(new JButton(), texts.get("button.copy"));
        copy.addActionListener(e -> chooseSelected());
        delete = Mnemonics.apply(new JButton(), texts.get("button.delete"));
        delete.addActionListener(e -> deleteSelected());
        clear = Mnemonics.apply(new JButton(), texts.get("button.clear"));
        clear.addActionListener(e -> clearAll());
        JButton close = new JButton(texts.get("button.close"));
        close.addActionListener(e -> setVisible(false));

        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(0, 2, 6, 2));
        top.add(hint, BorderLayout.CENTER);

        scroll.setPreferredSize(new Dimension(460, ROW_HEIGHT * 8 + 4));
        scroll.getVerticalScrollBar().setUnitIncrement(ROW_HEIGHT);
        center.setBorder(AmadeUI.sunken());

        JPanel buttons = new JPanel(new GridLayout(1, 0, 8, 0));
        buttons.add(copy);
        buttons.add(delete);
        buttons.add(clear);
        buttons.add(close);
        JPanel bottom = new JPanel(new BorderLayout(12, 0));
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        bottom.add(pause, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.add(buttons);
        bottom.add(right, BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        root.add(top, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);
        keys(root);
        getRootPane().setDefaultButton(copy);
        refresh();
        pack();
    }

    private void keys(JComponent root) {
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close", () -> setVisible(false));
        bind(list, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "choose", this::chooseSelected);
        bind(list, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete", this::deleteSelected);
        for (int i = 1; i <= 9; i++) {
            int index = i - 1;
            Runnable pick = () -> {
                if (index < model.size()) {
                    list.setSelectedIndex(index);
                    chooseSelected();
                }
            };
            bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_0 + i, 0), "pick" + i, pick);
            bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0 + i, 0), "pad" + i, pick);
        }
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

    /** Shows the window next to the mouse pointer with the newest entry selected. */
    void showAtPointer() {
        refresh();
        if (!model.isEmpty()) list.setSelectedIndex(0);
        if (!isVisible()) placeAtPointer();
        setVisible(true);
        setExtendedState(NORMAL);
        toFront();
        (model.isEmpty() ? pause : list).requestFocusInWindow();
    }

    private void placeAtPointer() {
        PointerInfo pointer = MouseInfo.getPointerInfo();
        if (pointer == null) {
            setLocationRelativeTo(null);
            return;
        }
        GraphicsConfiguration gc = pointer.getDevice().getDefaultConfiguration();
        Rectangle screen = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        screen = new Rectangle(screen.x + insets.left, screen.y + insets.top,
                screen.width - insets.left - insets.right, screen.height - insets.top - insets.bottom);
        Point p = pointer.getLocation();
        int x = Math.max(screen.x, Math.min(p.x - getWidth() / 2, screen.x + screen.width - getWidth()));
        int y = Math.max(screen.y, Math.min(p.y - 20, screen.y + screen.height - getHeight()));
        setLocation(x, y);
    }

    /** Shows the history as it is now; keeps the selected entry selected. */
    void refresh() {
        ClipEntry selected = list.getSelectedValue();
        List<ClipEntry> entries = actions.entries();
        model.clear();
        entries.forEach(model::addElement);
        thumbnails.keySet().retainAll(new java.util.HashSet<>(entries));
        if (selected != null && entries.contains(selected)) list.setSelectedValue(selected, true);
        center.removeAll();
        center.add(model.isEmpty() ? empty : scroll);
        center.revalidate();
        center.repaint();
        pause.setSelected(actions.paused());
        pause.setEnabled(actions.historyOn());
        updateHint();
        updateButtons();
    }

    private void updateHint() {
        hint.setText(!actions.historyOn() ? texts.get("hint.off")
                : actions.paused() ? texts.get("hint.paused") : texts.get("hint"));
    }

    private void updateButtons() {
        boolean selected = list.getSelectedValue() != null;
        copy.setEnabled(selected);
        delete.setEnabled(selected);
        clear.setEnabled(!model.isEmpty());
    }

    private void chooseSelected() {
        ClipEntry entry = list.getSelectedValue();
        if (entry == null) return;
        actions.choose(entry);
        setVisible(false);
    }

    private void deleteSelected() {
        int index = list.getSelectedIndex();
        ClipEntry entry = list.getSelectedValue();
        if (entry == null) return;
        actions.remove(entry);
        model.removeElement(entry);
        if (!model.isEmpty()) list.setSelectedIndex(Math.min(index, model.size() - 1));
        refresh();
    }

    private void clearAll() {
        if (model.isEmpty()) return;
        int answer = JOptionPane.showConfirmDialog(this, texts.format("clear.confirm", model.size()),
                texts.get("title"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return;
        actions.clear();
        model.clear();
        refresh();
    }

    List<ClipEntry> shown() {
        return java.util.Collections.list(model.elements());
    }

    private Icon iconOf(ClipEntry entry) {
        if (entry.kind() == ClipEntry.Kind.IMAGE || entry.image() != null) {
            return thumbnails.computeIfAbsent(entry, e -> new ImageIcon(EntryPreview.thumbnail(e.image())));
        }
        return new KindIcon(entry.kind());
    }

    /** A row: number, thumbnail or symbol, the content line and the line about the entry. */
    private final class Renderer extends JPanel implements ListCellRenderer<ClipEntry> {
        private final JLabel number = new JLabel();
        private final JLabel icon = new JLabel();
        private final JLabel line = new JLabel();
        private final JLabel meta = new JLabel();

        Renderer() {
            super(new BorderLayout(8, 0));
            setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            number.setPreferredSize(new Dimension(14, 10));
            icon.setPreferredSize(new Dimension(EntryPreview.THUMB_WIDTH, EntryPreview.THUMB_HEIGHT));
            icon.setHorizontalAlignment(SwingConstants.CENTER);
            for (JLabel l : List.of(number, line, meta)) l.putClientProperty("html.disable", Boolean.TRUE);
            meta.setFont(meta.getFont().deriveFont(11f));
            JPanel left = new JPanel(new BorderLayout(4, 0));
            left.setOpaque(false);
            left.add(number, BorderLayout.WEST);
            left.add(icon, BorderLayout.CENTER);
            JPanel text = new JPanel(new GridLayout(2, 1));
            text.setOpaque(false);
            text.add(line);
            text.add(meta);
            add(left, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ClipEntry> list, ClipEntry entry, int index,
                                                      boolean selected, boolean focused) {
            DesktopTheme t = AmadeUI.theme();
            Color fg = selected ? list.getSelectionForeground() : list.getForeground();
            setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            number.setText(index < 9 ? Integer.toString(index + 1) : "");
            icon.setIcon(iconOf(entry));
            String text = EntryPreview.line(entry);
            line.setText(text.isEmpty() ? " " : text);
            meta.setText(EntryPreview.meta(entry, texts, zone));
            number.setForeground(fg);
            line.setForeground(fg);
            meta.setForeground(selected ? fg : t.textInactive());
            return this;
        }
    }

    /** The symbol of a text or file entry: lines of text, or a folder. */
    private record KindIcon(ClipEntry.Kind kind) implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x + (getIconWidth() - 28) / 2, y + (getIconHeight() - 28) / 2);
            DesktopTheme t = AmadeUI.theme();
            g2.setStroke(new BasicStroke(1.4f));
            if (kind == ClipEntry.Kind.FILES) {
                g2.setColor(new Color(0xD8, 0xB0, 0x50));
                g2.fillRect(3, 8, 22, 15);
                g2.fillRect(3, 5, 9, 4);
                g2.setColor(t.textActive());
                g2.drawRect(3, 8, 22, 15);
            } else {
                g2.setColor(t.fieldBackground());
                g2.fillRect(5, 2, 18, 24);
                g2.setColor(t.textActive());
                g2.drawRect(5, 2, 18, 24);
                for (int row = 7; row <= 21; row += 4) g2.drawLine(8, row, row == 21 ? 15 : 20, row);
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return EntryPreview.THUMB_WIDTH;
        }

        @Override
        public int getIconHeight() {
            return EntryPreview.THUMB_HEIGHT;
        }
    }
}
