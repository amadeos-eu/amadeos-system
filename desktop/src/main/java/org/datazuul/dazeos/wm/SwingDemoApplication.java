package org.datazuul.dazeos.wm;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public final class SwingDemoApplication {
    private static final AtomicInteger WINDOW_COUNTER = new AtomicInteger(0);

    private SwingDemoApplication() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SwingDemoApplication::showDemo);
    }

    private static void showDemo() {
        JFrame mainFrame = new JFrame("DazeOS Desktop - Control Panel");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setLayout(new BorderLayout(10, 10));

        JPanel headerPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        JLabel titleLabel = new JLabel("DazeOS Java Window Manager (M4)", JLabel.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(16.0f));
        JLabel statusLabel = new JLabel("Click below to test real multi-window management and dialog centering.", JLabel.CENTER);
        headerPanel.add(titleLabel);
        headerPanel.add(statusLabel);
        mainFrame.add(headerPanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));

        JButton newWindowBtn = new JButton("Open New Window");
        newWindowBtn.addActionListener(e -> spawnDocumentWindow());

        JButton dialogBtn = new JButton("Open Modal Dialog");
        dialogBtn.addActionListener(e -> {
            JOptionPane.showMessageDialog(
                    mainFrame,
                    "This is a transient dialog managed by DazeOS.\nNotice how it centers over the parent window!",
                    "DazeOS Dialog",
                    JOptionPane.INFORMATION_MESSAGE);
        });

        JButton terminalBtn = new JButton("Launch Terminal (xterm)");
        terminalBtn.addActionListener(e -> {
            try {
                new ProcessBuilder("xterm").start();
                statusLabel.setText("Launched xterm terminal window.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(mainFrame, "Failed to launch xterm: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        buttonPanel.add(newWindowBtn);
        buttonPanel.add(dialogBtn);
        buttonPanel.add(terminalBtn);
        mainFrame.add(buttonPanel, BorderLayout.CENTER);

        JLabel footerLabel = new JLabel("DazeOS Live System | Non-reparenting Java WM with ICCCM & EWMH", JLabel.CENTER);
        footerLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        mainFrame.add(footerLabel, BorderLayout.SOUTH);

        mainFrame.setPreferredSize(new Dimension(540, 180));
        mainFrame.pack();
        mainFrame.setLocationByPlatform(true);
        mainFrame.setVisible(true);
    }

    private static void spawnDocumentWindow() {
        int winNum = WINDOW_COUNTER.incrementAndGet();
        JFrame docFrame = new JFrame("Document Window #" + winNum);
        docFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        docFrame.setLayout(new BorderLayout(8, 8));

        JTextArea textArea = new JTextArea(
                "Welcome to DazeOS Window #" + winNum + "!\n\n"
                + "- Click anywhere in this window to focus and raise it.\n"
                + "- You can type text, resize, or open more windows.\n"
                + "- Close this window when finished.");
        textArea.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        docFrame.add(new JScrollPane(textArea), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        AtomicInteger clickCounter = new AtomicInteger(0);
        JButton counterBtn = new JButton("Clicks: 0");
        counterBtn.addActionListener(e -> counterBtn.setText("Clicks: " + clickCounter.incrementAndGet()));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> docFrame.dispose());

        bottomPanel.add(counterBtn);
        bottomPanel.add(closeBtn);
        docFrame.add(bottomPanel, BorderLayout.SOUTH);

        docFrame.setPreferredSize(new Dimension(420, 260));
        docFrame.pack();
        docFrame.setLocationByPlatform(true);
        docFrame.setVisible(true);
    }
}

