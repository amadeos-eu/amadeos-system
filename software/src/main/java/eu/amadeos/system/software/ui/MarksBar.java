package eu.amadeos.system.software.ui;

import eu.amadeos.commons.i18n.Messages;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/**
 * Above the progress bar while packages are marked: how many are marked to install, update and remove, in
 * all tabs together, with Clear marks and Apply changes, which does them all in one go.
 */
final class MarksBar extends JPanel {
    private final Messages msg;
    private final Marks marks;
    private final JLabel text = new JLabel(" ");
    private final JButton clear;
    private final JButton apply;
    private boolean busy;

    MarksBar(Messages msg, Marks marks, Runnable onApply) {
        super(new BorderLayout(8, 0));
        this.msg = msg;
        this.marks = marks;
        setBorder(BorderFactory.createEmptyBorder(6, 8, 0, 8));
        text.setFont(text.getFont().deriveFont(Font.BOLD));
        clear = new JButton(msg.get("action.clear-marks"));
        clear.addActionListener(e -> marks.clear());
        apply = new JButton(msg.get("action.apply"));
        apply.addActionListener(e -> onApply.run());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(clear);
        buttons.add(apply);
        JPanel line = new JPanel(new BorderLayout(8, 0));
        line.setOpaque(false);
        line.add(text, BorderLayout.CENTER);
        line.add(buttons, BorderLayout.EAST);
        add(new JSeparator(), BorderLayout.NORTH);
        add(line, BorderLayout.CENTER);
        marks.addListener(this::update);
        update();
    }

    /** "3 packages are marked: 1 to install, 2 to update." */
    static String summary(Messages msg, Marks marks) {
        List<String> parts = new ArrayList<>();
        int install = marks.count(Marks.Mark.INSTALL);
        int update = marks.count(Marks.Mark.UPDATE);
        int remove = marks.count(Marks.Mark.REMOVE);
        if (install > 0) parts.add(msg.format("marks.install", install));
        if (update > 0) parts.add(msg.format("marks.update", update));
        if (remove > 0) parts.add(msg.format("marks.remove", remove));
        return msg.format("marks.summary", marks.size(), String.join(", ", parts));
    }

    void setBusy(boolean busy) {
        this.busy = busy;
        update();
    }

    private void update() {
        setVisible(!marks.isEmpty());
        if (!marks.isEmpty()) text.setText(summary(msg, marks));
        clear.setEnabled(!busy);
        apply.setEnabled(!busy && !marks.isEmpty());
        revalidate();
    }

    String text() {
        return text.getText();
    }

    JButton applyButton() {
        return apply;
    }

    JButton clearButton() {
        return clear;
    }
}
