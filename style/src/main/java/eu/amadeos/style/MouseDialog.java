package eu.amadeos.style;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.input.InputSettings;
import eu.amadeos.input.XCommands;
import eu.amadeos.style.InputControls.LabeledSlider;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * Mouse: handedness, double-click time (with a target that reacts to double clicks at the chosen speed),
 * acceleration and threshold.
 */
final class MouseDialog extends StyleDialog {
    private final InputControls controls;
    private final JRadioButton right = new JRadioButton("Right-handed");
    private final JRadioButton left = new JRadioButton("Left-handed");
    private final LabeledSlider doubleClick;
    private final LabeledSlider acceleration;
    private final LabeledSlider threshold;

    MouseDialog(JFrame owner, Consumer<String> status, InputControls controls) {
        super(owner, StyleCategory.MOUSE, status);
        this.controls = controls;
        InputSettings.Mouse m = controls.load().mouse();
        ButtonGroup hands = new ButtonGroup();
        hands.add(right);
        hands.add(left);
        (m.leftHanded() ? left : right).setSelected(true);
        JPanel handRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        handRow.add(right);
        handRow.add(left);

        doubleClick = new LabeledSlider(100, 1500, m.doubleClickMs(), v -> v + " ms");
        acceleration = new LabeledSlider(1, 10, m.acceleration(), v -> v == 1 ? "None" : v + " ×");
        threshold = new LabeledSlider(1, 50, m.threshold(), v -> v + " px");
        DoubleClickTarget target = new DoubleClickTarget(doubleClick);
        JButton defaults = new JButton("Defaults");
        defaults.addActionListener(e -> show(InputSettings.Mouse.DEFAULTS));

        JPanel form = InputControls.form(
                "Handedness:", handRow,
                "Double-click:", doubleClick,
                "Acceleration:", acceleration,
                "Threshold:", threshold);
        JPanel bottom = new JPanel(new BorderLayout(10, 0));
        JPanel defaultsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        defaultsRow.add(defaults);
        bottom.add(defaultsRow, BorderLayout.WEST);
        bottom.add(target, BorderLayout.EAST);
        bottom.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        JPanel content = new JPanel(new BorderLayout());
        content.add(form, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        layoutDialog(content);
    }

    private void show(InputSettings.Mouse m) {
        (m.leftHanded() ? left : right).setSelected(true);
        doubleClick.setValue(m.doubleClickMs());
        acceleration.setValue(m.acceleration());
        threshold.setValue(m.threshold());
    }

    InputSettings.Mouse settings() {
        return new InputSettings.Mouse(left.isSelected(), doubleClick.value(), acceleration.value(), threshold.value());
    }

    @Override
    protected String apply() {
        InputSettings.Mouse m = settings();
        String message = controls.saveAndApply(controls.load().withMouse(m),
                XCommands.mouse(m, controls.pointerButtons()), "Mouse");
        return message + " (double-click time applies to programs started from now on)";
    }

    /** Toggles between closed and open when double-clicked within the slider's time, like dtstyle's test box. */
    static final class DoubleClickTarget extends JComponent {
        private final LabeledSlider time;
        private long lastClick;
        private boolean open;

        DoubleClickTarget(LabeledSlider time) {
            this.time = time;
            setPreferredSize(new Dimension(150, 44));
            setToolTipText("Double-click here to test the double-click time");
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    long now = e.getWhen();
                    if (now - lastClick <= time.value()) {
                        open = !open;
                        lastClick = 0;
                        repaint();
                    } else {
                        lastClick = now;
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            DesktopTheme t = AmadeUI.theme();
            g2.setColor(open ? t.selectionBackground() : t.sunkenBg());
            g2.fillRect(0, 0, getWidth(), getHeight());
            AmadeUI.paintBevel(g2, 0, 0, getWidth(), getHeight(), !open, 2);
            g2.setFont(getFont() == null ? new Font(Font.SANS_SERIF, Font.BOLD, 12) : getFont().deriveFont(Font.BOLD));
            g2.setColor(open ? t.selectionText() : t.textActive());
            String text = open ? "Double-click OK" : "Double-click test";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, (getHeight() + fm.getAscent()) / 2 - 2);
            g2.dispose();
        }
    }
}
