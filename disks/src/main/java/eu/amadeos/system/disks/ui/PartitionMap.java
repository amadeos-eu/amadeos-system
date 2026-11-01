package eu.amadeos.system.disks.ui;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.system.disks.model.Segment;
import eu.amadeos.system.disks.model.Volume;

import javax.swing.JComponent;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * The partition map: one coloured block per partition, hatched free space. Small partitions get a minimum width so
 * they can be clicked. A click or the arrow keys select a block.
 */
final class PartitionMap extends JComponent {
    static final Color EFI = new Color(0x8E, 0xA9, 0xC8);
    static final Color EXT = new Color(0x7F, 0xB0, 0x7A);
    static final Color SWAP = new Color(0xC9, 0x9A, 0xC9);
    static final Color FAT = new Color(0xE0, 0xB8, 0x6A);
    static final Color EXFAT = new Color(0xE6, 0x9C, 0x5A);
    static final Color NTFS = new Color(0x7A, 0x9C, 0xD6);
    static final Color CRYPT = new Color(0xA8, 0x9A, 0xB8);
    static final Color OTHER = new Color(0xB8, 0xB4, 0xAA);
    static final Color FREE = new Color(0xE8, 0xE4, 0xDA);
    private static final int MIN_WIDTH = 44;

    private final Context context;
    private final IntConsumer onSelect;
    private List<Segment> segments = List.of();
    private int selected = -1;
    private int[] edges = new int[0];

    PartitionMap(Context context, IntConsumer onSelect) {
        this.context = context;
        this.onSelect = onSelect;
        setFocusable(true);
        setFont(UIManager.getFont("Label.font"));
        setPreferredSize(new Dimension(520, 64));
        setMinimumSize(new Dimension(200, 64));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                for (int i = 0; i + 1 < edges.length; i++) {
                    if (e.getX() >= edges[i] && e.getX() < edges[i + 1]) select(i);
                }
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_LEFT && selected > 0) select(selected - 1);
                if (e.getKeyCode() == KeyEvent.VK_RIGHT && selected + 1 < segments.size()) select(selected + 1);
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
    }

    void setSegments(List<Segment> list, int select) {
        segments = List.copyOf(list);
        selected = segments.isEmpty() ? -1 : Math.max(0, Math.min(select, segments.size() - 1));
        repaint();
    }

    void select(int index) {
        if (index == selected) return;
        selected = index;
        repaint();
        onSelect.accept(index);
    }

    int selected() {
        return selected;
    }

    static Color color(Volume v) {
        if (v.encrypted()) return CRYPT;
        if (v.mountPoints().contains("/boot/efi") || v.mountPoints().contains("/efi")) return EFI;
        return switch (v.type()) {
            case "vfat" -> FAT;
            case "exfat" -> EXFAT;
            case "ext2", "ext3", "ext4", "btrfs", "xfs", "f2fs" -> EXT;
            case "ntfs" -> NTFS;
            case "swap" -> SWAP;
            default -> OTHER;
        };
    }

    /** Block edges: sizes in proportion, each at least {@link #MIN_WIDTH} wide where there is room. */
    static int[] edges(List<Segment> segments, int width) {
        int n = segments.size();
        int[] out = new int[n + 1];
        if (n == 0) return out;
        long total = 0;
        for (Segment s : segments) total += s.size();
        int[] w = new int[n];
        int min = Math.min(MIN_WIDTH, width / n);
        int fixed = 0;
        long flexible = 0;
        for (int i = 0; i < n; i++) {
            double share = (double) segments.get(i).size() / Math.max(1, total);
            if (share * width < min) {
                w[i] = min;
                fixed += min;
            } else {
                flexible += segments.get(i).size();
            }
        }
        int rest = Math.max(0, width - fixed);
        for (int i = 0; i < n; i++) {
            if (w[i] == 0) w[i] = (int) Math.round((double) segments.get(i).size() / Math.max(1, flexible) * rest);
        }
        int x = 0;
        for (int i = 0; i < n; i++) {
            out[i] = x;
            x += w[i];
        }
        out[n] = width;
        return out;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g.setColor(AmadeUI.theme().sunkenBg());
        g.fillRect(0, 0, w, h);
        int inner = w - 4;
        int[] e = edges(segments, inner);
        edges = new int[e.length];
        for (int i = 0; i < e.length; i++) edges[i] = e[i] + 2;
        if (segments.isEmpty()) {
            g.setColor(AmadeUI.theme().textInactive());
            String s = context.texts().get("map.no-medium");
            FontMetrics fm = g.getFontMetrics();
            g.drawString(s, (w - fm.stringWidth(s)) / 2, (h + fm.getAscent()) / 2 - 2);
        }
        for (int i = 0; i < segments.size(); i++) {
            Segment s = segments.get(i);
            int x = edges[i], bw = edges[i + 1] - edges[i];
            paintBlock(g, s, x, 2, bw, h - 4, i == selected);
        }
        AmadeUI.paintBevel(g, 0, 0, w, h, false, 2);
        g.dispose();
    }

    private void paintBlock(Graphics2D g, Segment s, int x, int y, int bw, int bh, boolean sel) {
        if (s.free()) {
            g.setColor(FREE);
            g.fillRect(x, y, bw, bh);
            Shape clip = g.getClip();
            g.clipRect(x, y, bw, bh);
            g.setColor(new Color(0xC8, 0xC2, 0xB4));
            for (int k = -bh; k < bw; k += 8) g.drawLine(x + k, y + bh, x + k + bh, y);
            g.setColor(FREE);
            g.fillRect(x + 4, y + bh / 2 - 16, Math.max(0, bw - 8), 32);
            g.setClip(clip);
        } else {
            g.setColor(color(s.volume()));
            g.fillRect(x, y, bw, bh);
        }
        AmadeUI.paintBevel(g, x, y, bw, bh, true, 1);
        List<String> lines = new ArrayList<>();
        if (s.free()) {
            lines.add(context.texts().get("map.free"));
        } else {
            lines.add(context.volumeName(s.volume()));
        }
        String fs = s.free() ? "" : context.fsShort(s.volume());
        String size = context.size(s.size());
        g.setColor(Color.BLACK);
        g.setFont(getFont().deriveFont(java.awt.Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        int cx = x + bw / 2;
        String top = fit(lines.get(0), fm, bw - 8);
        if (!top.isEmpty()) g.drawString(top, cx - fm.stringWidth(top) / 2, y + bh / 2 - 3);
        g.setFont(getFont());
        fm = g.getFontMetrics();
        String bottom = fs.isEmpty() ? size : fs + " · " + size;
        if (fm.stringWidth(bottom) + 6 > bw) bottom = size;
        bottom = fit(bottom, fm, bw - 6);
        if (!bottom.isEmpty()) g.drawString(bottom, cx - fm.stringWidth(bottom) / 2, y + bh / 2 + fm.getAscent());
        if (sel) {
            g.setColor(AmadeUI.theme().selectionBackground());
            g.setStroke(new BasicStroke(3));
            g.drawRect(x + 1, y + 1, bw - 3, bh - 3);
            if (isFocusOwner()) {
                g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{1, 2}, 0));
                g.setColor(Color.BLACK);
                g.drawRect(x + 4, y + 4, bw - 9, bh - 9);
            }
            g.setStroke(new BasicStroke(1));
        }
    }

    private static String fit(String s, FontMetrics fm, int width) {
        if (fm.stringWidth(s) <= width) return s;
        for (int n = s.length() - 1; n > 0; n--) {
            String t = s.substring(0, n) + "…";
            if (fm.stringWidth(t) <= width) return t;
        }
        return "";
    }
}
