package org.datazuul.amadeos.wm.panel.widget;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.List;
import org.datazuul.amadeos.base.process.ProcessLauncher;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.Timer;
import org.datazuul.amadeos.commons.theme.DesktopTheme;

/**
 * Authentic CDE Workstation Front Panel Calendar Dock Widget:
 * - Positioned directly to the right of the ClockDockWidget.
 * - Shows a tear-off paper calendar sheet with current Month above Day.
 * - Pixel-exact to the original CDE "Jul 27" front panel calendar icon:
 *   - Dark backing pad with left sheet-stack highlight.
 *   - Two top binder rings/slits.
 *   - Turn-page / dog-ear curled corner at bottom right.
 *   - Crisp, centered black month and day typography.
 *   - Raised drawer notch tab on top without triangle arrow.
 * - Seamless button plate matching the rest of the launcher row.
 */
public final class CalendarDockWidget extends AbstractDockWidget {
    private static final DateTimeFormatter TOOLTIP_FMT = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");

    // Dimensions of the tear-off calendar sheet
    private static final int SHEET_W = 35;
    private static final int SHEET_H = 43;

    // Pixel matrix extracted from the authentic CDE Front Panel calendar icon (35x43)
    private static final String[] TEMPLATE = {
        "B1BB .-BBBBBBBBBBBBBBBBBBBBBB .-xBB ",
        "B::= .-:::::::::::::::::::::: .-#=B ",
        "B:-: .s=sx::::::::::::::::::: .s==B ",
        "B:-:-:ssx====================-:sssB ",
        "B:-:s=sx=====================s=sxsB ",
        "B:-:0s0======================0s0=sB ",
        "B:-:=0========================0==sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:=============================sB ",
        "B:-:========================...:::B ",
        "B:-:========================..:=-#B ",
        "B:-:========================.:=-#xB ",
        "B:-:========================:=-#x=B ",
        "B:-:========================:-#x==B ",
        "B:-:========================:#x===B ",
        "B:-ssssssssssssssssssssssssssssss0B ",
        "B:##xxxxxxxxxxxxxxxxxxxxxxxxxxxx00B ",
        "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB "
    };

    private final JPanel panel;
    private final Timer timer;
    private final JPopupMenu contextMenu;

    public CalendarDockWidget() {
        super("calendar");

        panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                DesktopTheme t = currentTheme;
                Graphics2D g2 = (Graphics2D) g.create();

                int w = getWidth();
                int h = getHeight();

                // 1. Raised drawer tab without triangle + seamless button plate
                int tabH = CdePanelChrome.TAB_HEIGHT;
                CdePanelChrome.paintSlotBay(g2, w, h, t);

                // 2. Render paper calendar sheet on the plate
                paintCalendarSheet(g2, w, h, tabH + 1, t);

                g2.dispose();
            }
        };

        panel.setPreferredSize(new Dimension(CdePanelChrome.SLOT_WIDTH, CdePanelChrome.SLOT_HEIGHT));
        panel.setBackground(currentTheme.base());
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Context menu
        contextMenu = new JPopupMenu();
        JMenuItem todayItem = new JMenuItem("Show Today's Date...");
        todayItem.addActionListener(e -> showTodayDialog());
        contextMenu.add(todayItem);

        panel.setComponentPopupMenu(contextMenu);

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    launchCalendarApp();
                }
            }
        });

        updateTooltip();

        // Update every 30 seconds to catch midnight date changes
        timer = new Timer(30_000, e -> {
            updateTooltip();
            panel.repaint();
        });
    }

    private void updateTooltip() {
        LocalDate today = LocalDate.now();
        panel.setToolTipText("Calendar (" + today.format(TOOLTIP_FMT) + ") - Click to launch");
    }

    private void paintCalendarSheet(Graphics2D g2, int w, int h, int topOffset, DesktopTheme t) {
        int sheetX = (w - SHEET_W) / 2; // (58 - 35) / 2 = 11
        int sheetY = topOffset + (h - topOffset - SHEET_H) / 2; // 16 + (59 - 43) / 2 = 24

        Color padBorder = t.darkShadow();
        Color padHighlight = new Color(0x21, 0x21, 0x21);
        Color white = Color.WHITE;
        Color silver = new Color(0xAD, 0xAD, 0xAD);
        Color lightPaper = new Color(0xDE, 0xDE, 0xDE);
        Color paperBase = new Color(0xBD, 0xBD, 0xBD);
        Color softShadow = new Color(0x94, 0x94, 0x94);
        Color shadow = new Color(0x73, 0x73, 0x73);
        Color darkShadow = new Color(0x63, 0x63, 0x63);
        Color ink = Color.BLACK;

        // Draw pixel template
        for (int row = 0; row < TEMPLATE.length; row++) {
            String line = TEMPLATE[row];
            int py = sheetY + row;
            for (int col = 0; col < line.length(); col++) {
                char ch = line.charAt(col);
                if (ch == ' ') {
                    continue; // Transparent, let button plate show
                }
                Color c = switch (ch) {
                    case 'B' -> padBorder;
                    case '1' -> padHighlight;
                    case '.' -> white;
                    case '-' -> silver;
                    case ':' -> lightPaper;
                    case '=' -> paperBase;
                    case 's' -> softShadow;
                    case 'x' -> shadow;
                    case '#' -> darkShadow;
                    case '0' -> ink;
                    default -> null;
                };
                if (c != null) {
                    g2.setColor(c);
                    g2.fillRect(sheetX + col, py, 1, 1);
                }
            }
        }

        // Render current month and day text onto the paper sheet
        LocalDate today = LocalDate.now();
        String monthStr = today.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        // Capitalize first letter only (e.g. "Jul", "Oct")
        if (monthStr.length() > 1) {
            monthStr = monthStr.substring(0, 1).toUpperCase() + monthStr.substring(1).toLowerCase();
        }
        String dayStr = String.valueOf(today.getDayOfMonth());

        Graphics2D gt = (Graphics2D) g2.create();
        gt.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        gt.setColor(ink);

        // Center of paper sheet area: sheetX + 3 to sheetX + 32 -> center is sheetX + 18
        int centerX = sheetX + 18;

        // Month text (e.g. "Jul", "Oct")
        Font monthFont = new Font(Font.SERIF, Font.PLAIN, 12);
        gt.setFont(monthFont);
        FontMetrics fmMonth = gt.getFontMetrics();
        int monthX = centerX - fmMonth.stringWidth(monthStr) / 2;
        int monthY = sheetY + 18;
        gt.drawString(monthStr, monthX, monthY);

        // Day of month text (e.g. "27", "11")
        Font dayFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        gt.setFont(dayFont);
        FontMetrics fmDay = gt.getFontMetrics();
        int dayX = centerX - fmDay.stringWidth(dayStr) / 2;
        int dayY = sheetY + 32;
        gt.drawString(dayStr, dayX, dayY);

        gt.dispose();
    }

    private void launchCalendarApp() {
        // amade-calendar once it exists (see ROADMAP M13), else an informative dialog
        try {
            if (ProcessLauncher.startFirstAvailable(List.of(List.of("amade-calendar"))).isPresent()) {
                return;
            }
        } catch (Exception ignored) {
            // fall back to the date dialog
        }

        // Fallback: Show today's date dialog
        showTodayDialog();
    }

    private void showTodayDialog() {
        LocalDate today = LocalDate.now();
        String msg = "Today is:\n" + today.format(TOOLTIP_FMT);
        JOptionPane.showMessageDialog(panel, msg, "amadeOS Calendar", JOptionPane.INFORMATION_MESSAGE);
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
        panel.setBackground(theme.base());
        panel.repaint();
    }
}
