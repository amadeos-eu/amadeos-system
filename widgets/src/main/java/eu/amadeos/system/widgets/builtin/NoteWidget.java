package eu.amadeos.system.widgets.builtin;

import eu.amadeos.commons.widget.DesktopWidget;
import eu.amadeos.commons.widget.WidgetColors;
import eu.amadeos.commons.widget.WidgetContext;
import eu.amadeos.commons.widget.WidgetSettings;
import eu.amadeos.system.widgets.WidgetTexts;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * A to-do note: a title, the things to do with a box to tick, a line to add one, and Remove done. The list is
 * kept in the copy's settings, one line per entry, {@code [x] } before the done ones.
 */
public final class NoteWidget implements DesktopWidget {
    /** One thing to do. */
    record Item(String text, boolean done) {
        String line() {
            return (done ? "[x] " : "[ ] ") + text;
        }

        static Item parse(String line) {
            if (line.startsWith("[x] ")) return new Item(line.substring(4), true);
            if (line.startsWith("[ ] ")) return new Item(line.substring(4), false);
            return new Item(line, false);
        }
    }

    /** The redraw of each copy, so that a new title shows at once. */
    private static final Map<WidgetContext, Runnable> REFRESH = new java.util.WeakHashMap<>();

    @Override
    public String id() {
        return "note";
    }

    @Override
    public String name() {
        return WidgetTexts.get("note.name");
    }

    @Override
    public String description() {
        return WidgetTexts.get("note.description");
    }

    @Override
    public Dimension defaultSize() {
        return new Dimension(260, 260);
    }

    static List<Item> items(WidgetContext context) {
        String text = context.get("items", "");
        if (text.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.stream(text.split("\n")).filter(s -> !s.isBlank()).map(Item::parse).toList());
    }

    static void store(WidgetContext context, List<Item> items) {
        context.set("items", String.join("\n", items.stream().map(Item::line).toList()));
    }

    @Override
    public JComponent create(WidgetContext context) {
        JPanel root = new JPanel(new BorderLayout(0, 6));
        root.setOpaque(false);
        JLabel title = new JLabel();
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        JTextField add = new JTextField();
        // A plain link rather than a button, so the card stays calm
        JLabel clear = new JLabel(WidgetTexts.get("note.clear"));
        clear.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 2));
        clear.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        JPanel south = new JPanel(new BorderLayout(4, 0));
        south.setOpaque(false);
        south.add(add, BorderLayout.CENTER);
        south.add(clear, BorderLayout.EAST);

        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            WidgetColors c = context.colors();
            title.setText(context.get("title", WidgetTexts.get("note.title")));
            title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            title.setForeground(c.accent());
            list.removeAll();
            List<Item> items = items(context);
            for (int i = 0; i < items.size(); i++) {
                Item item = items.get(i);
                int index = i;
                JCheckBox box = new JCheckBox(item.text(), item.done());
                box.setOpaque(false);
                box.setFocusPainted(false);
                box.setForeground(item.done() ? c.dim() : c.text());
                Font f = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
                if (item.done()) f = f.deriveFont(Map.of(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON));
                box.setFont(f);
                box.setAlignmentX(Component.LEFT_ALIGNMENT);
                box.addActionListener(e -> {
                    List<Item> now = items(context);
                    if (index < now.size()) {
                        Item it = now.get(index);
                        now.set(index, new Item(it.text(), box.isSelected()));
                        store(context, now);
                    }
                    refresh[0].run();
                });
                list.add(box);
            }
            if (items.isEmpty()) {
                JLabel empty = new JLabel(WidgetTexts.get("note.empty"));
                empty.setForeground(c.dim());
                list.add(empty);
            }
            list.add(Box.createVerticalGlue());
            clear.setForeground(c.dim());
            clear.setVisible(items.stream().anyMatch(Item::done));
            list.revalidate();
            list.repaint();
        };
        add.addActionListener(e -> {
            String text = add.getText().strip();
            if (text.isEmpty()) return;
            List<Item> items = items(context);
            items.add(new Item(text.replace('\n', ' '), false));
            store(context, items);
            add.setText("");
            refresh[0].run();
        });
        add.setToolTipText(WidgetTexts.get("note.add"));
        clear.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                List<Item> items = items(context);
                items.removeIf(Item::done);
                store(context, items);
                refresh[0].run();
            }
        });
        context.onThemeChange(refresh[0]);
        refresh[0].run();
        root.add(title, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        REFRESH.put(context, refresh[0]);
        return root;
    }

    @Override
    public WidgetSettings settings(WidgetContext context) {
        JTextField title = new JTextField(context.get("title", WidgetTexts.get("note.title")), 24);
        JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
        form.add(new JLabel(WidgetTexts.get("note.title.label")));
        form.add(title);
        return new WidgetSettings(form, () -> {
            context.set("title", title.getText().strip());
            Runnable refresh = REFRESH.get(context);
            if (refresh != null) refresh.run();
        });
    }
}
