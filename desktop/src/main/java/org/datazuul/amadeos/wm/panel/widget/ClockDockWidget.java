package org.datazuul.amadeos.wm.panel.widget;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import org.datazuul.amadeos.wm.theme.DesktopTheme;

public final class ClockDockWidget extends AbstractDockWidget {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd");

    private final JPanel panel;
    private final JLabel timeLabel;
    private final JLabel dateLabel;
    private final Timer timer;

    public ClockDockWidget() {
        super("clock");
        panel = new JPanel(new GridLayout(2, 1, 0, 1));
        panel.setPreferredSize(new Dimension(66, 48));
        panel.setBorder(new MotifBevelBorder(false, 2));

        timeLabel = new JLabel(LocalTime.now().format(TIME_FMT), JLabel.CENTER);
        timeLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));

        dateLabel = new JLabel(LocalDate.now().format(DATE_FMT).toUpperCase(), JLabel.CENTER);
        dateLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 8));

        panel.add(timeLabel);
        panel.add(dateLabel);

        updateThemeStyles(currentTheme);

        timer = new Timer(1000, e -> updateTime());
    }

    private void updateTime() {
        timeLabel.setText(LocalTime.now().format(TIME_FMT));
        dateLabel.setText(LocalDate.now().format(DATE_FMT).toUpperCase());
    }

    @Override
    public JComponent component() {
        return panel;
    }

    @Override
    public void start() {
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    @Override
    public void stop() {
        timer.stop();
    }

    @Override
    protected void updateThemeStyles(DesktopTheme theme) {
        panel.setBackground(theme.sunkenBg());
        timeLabel.setForeground(theme.textActive());
        dateLabel.setForeground(theme.textActive());
    }
}

