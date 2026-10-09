package org.datazuul.dazeos.wm;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

public final class SwingDemoApplication {
    private SwingDemoApplication() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SwingDemoApplication::showDemo);
    }

    private static void showDemo() {
        WindowManagerCore manager = new WindowManagerCore(
                new WindowRegistry(), new LoggingX11Adapter(), new FloatingLayoutPolicy(20, 30));
        manager.manage(1, new WindowGeometry(0, 0, 420, 260));
        manager.manage(2, new WindowGeometry(0, 0, 360, 220));
        manager.arrange(1024, 768);

        JFrame frame = new JFrame("DazeOS Java WM MVP");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(8, 8));

        JLabel info = new JLabel("Swing demo; X11 operations are logged, not executed.", JLabel.CENTER);
        frame.add(info, BorderLayout.CENTER);

        AtomicInteger clickCount = new AtomicInteger(0);
        JButton arrange = new JButton("Arrange simulated windows (clicks: 0)");
        arrange.addActionListener(event -> {
            int clicks = clickCount.incrementAndGet();
            manager.arrange(1024, 768);
            arrange.setText("Arrange simulated windows (clicks: " + clicks + ")");
            info.setText("Arranged " + manager.registry().snapshot().size() + " windows! (Click #" + clicks + ")");
        });
        frame.add(arrange, BorderLayout.SOUTH);
        frame.setPreferredSize(new Dimension(560, 140));
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }
}

