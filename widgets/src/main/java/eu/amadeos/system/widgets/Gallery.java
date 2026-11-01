package eu.amadeos.system.widgets;

import eu.amadeos.commons.widget.DesktopWidget;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import java.util.function.Consumer;

/** The gallery: every widget there is, with what it shows and Add. */
final class Gallery extends JFrame {
    static final String TITLE = "amadeOS Widgets";

    Gallery(List<DesktopWidget> widgets, Consumer<DesktopWidget> add) {
        super(WidgetTexts.get("gallery.title"));
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JLabel intro = new JLabel(WidgetTexts.get("gallery.intro"));
        intro.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        intro.setAlignmentX(LEFT_ALIGNMENT);
        list.add(intro);
        for (DesktopWidget w : widgets) {
            JPanel row = new JPanel(new BorderLayout(12, 0));
            row.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
            JPanel text = new JPanel(new BorderLayout());
            JLabel name = new JLabel(w.name());
            name.setFont(name.getFont().deriveFont(Font.BOLD));
            text.add(name, BorderLayout.NORTH);
            text.add(new JLabel(w.description()), BorderLayout.CENTER);
            row.add(text, BorderLayout.CENTER);
            JButton addButton = new JButton(WidgetTexts.get("gallery.add"));
            addButton.addActionListener(e -> add.accept(w));
            JPanel east = new JPanel(new BorderLayout());
            east.add(addButton, BorderLayout.NORTH);
            row.add(east, BorderLayout.EAST);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            row.setAlignmentX(LEFT_ALIGNMENT);
            list.add(row);
        }
        list.add(Box.createVerticalGlue());
        JPanel root = new JPanel(new BorderLayout());
        root.add(new JScrollPane(list), BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.setBorder(BorderFactory.createEmptyBorder(6, 12, 10, 12));
        JLabel hint = new JLabel(WidgetTexts.get("gallery.hint"));
        south.add(hint, BorderLayout.CENTER);
        JButton close = new JButton(WidgetTexts.get("gallery.close"));
        close.addActionListener(e -> setVisible(false));
        south.add(close, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
        setSize(new Dimension(520, Math.min(560, 150 + widgets.size() * 58)));
        setLocationRelativeTo(null);
    }
}
