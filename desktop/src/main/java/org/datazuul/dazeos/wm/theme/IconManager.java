package org.datazuul.dazeos.wm.theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;

public final class IconManager {
    private static final Logger LOGGER = Logger.getLogger(IconManager.class.getName());
    private static final Map<String, Icon> ICON_CACHE = new ConcurrentHashMap<>();

    private IconManager() {}

    public static Icon getIcon(String key, int size) {
        String theme = ThemeManager.currentTheme().iconTheme();
        String cacheKey = theme + ":" + key + ":" + size;
        return ICON_CACHE.computeIfAbsent(cacheKey, k -> loadOrGenerateIcon(theme, key, size));
    }

    public static void clearCache() {
        ICON_CACHE.clear();
    }

    private static Icon loadOrGenerateIcon(String iconTheme, String key, int size) {
        // 1. Try user config directory: ~/.config/dazeos/themes/<iconTheme>/icons/<key>.png
        String userHome = System.getProperty("user.home", "/tmp");
        File userFile = new File(userHome + "/.config/dazeos/themes/" + iconTheme + "/icons/" + key + ".png");
        if (userFile.isFile()) {
            try {
                BufferedImage img = ImageIO.read(userFile);
                if (img != null) {
                    return scaleIcon(img, size);
                }
            } catch (Exception ignored) {}
        }

        // 2. Try system directory: /etc/dazeos/themes/<iconTheme>/icons/<key>.png
        File sysFile = new File("/etc/dazeos/themes/" + iconTheme + "/icons/" + key + ".png");
        if (sysFile.isFile()) {
            try {
                BufferedImage img = ImageIO.read(sysFile);
                if (img != null) {
                    return scaleIcon(img, size);
                }
            } catch (Exception ignored) {}
        }

        // 3. Try classpath resource: /themes/<iconTheme>/icons/<key>.png
        try (InputStream in = IconManager.class.getResourceAsStream("/themes/" + iconTheme + "/icons/" + key + ".png")) {
            if (in != null) {
                BufferedImage img = ImageIO.read(in);
                if (img != null) {
                    return scaleIcon(img, size);
                }
            }
        } catch (Exception ignored) {}

        // 4. Fallback to programmatic pixel/vector generator
        BufferedImage generated = generatePixmaps(key, size);
        return new ImageIcon(generated);
    }

    private static Icon scaleIcon(BufferedImage img, int size) {
        if (img.getWidth() == size && img.getHeight() == size) {
            return new ImageIcon(img);
        }
        Image scaled = img.getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    public static BufferedImage generatePixmaps(String key, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        float scale = size / 24.0f;
        g.scale(scale, scale);

        switch (key.toLowerCase()) {
            case "file-manager", "files" -> {
                // Manila folder
                g.setColor(new Color(0xD8, 0x9E, 0x3A)); // folder tab
                g.fillRect(3, 4, 8, 4);
                g.setColor(new Color(0xE8, 0xB6, 0x54)); // back body
                g.fillRect(3, 6, 18, 14);
                g.setColor(new Color(0xFF, 0xCE, 0x6E)); // front flap
                g.fillRect(2, 9, 20, 11);
                g.setColor(new Color(0x60, 0x40, 0x10)); // outline
                g.drawRect(2, 9, 19, 11);
                g.drawLine(3, 4, 11, 4);
                g.drawLine(11, 4, 13, 6);
                g.drawLine(13, 6, 20, 6);
                g.drawLine(20, 6, 20, 9);
            }
            case "text-editor", "editor", "doc" -> {
                // Document notepad with ruled lines
                g.setColor(Color.WHITE);
                g.fillRect(4, 3, 14, 18);
                g.setColor(new Color(0x40, 0x40, 0x40));
                g.drawRect(4, 3, 14, 18);
                // Folded corner
                g.setColor(new Color(0xD0, 0xD0, 0xD0));
                g.fillPolygon(new int[]{14, 18, 14}, new int[]{3, 7, 7}, 3);
                // Ruled lines
                g.setColor(new Color(0x50, 0x70, 0xA0));
                g.drawLine(7, 8, 13, 8);
                g.drawLine(7, 11, 15, 11);
                g.drawLine(7, 14, 15, 14);
                g.drawLine(7, 17, 12, 17);
                // Pencil
                g.setColor(new Color(0xDD, 0x88, 0x22));
                g.fillRect(16, 12, 4, 8);
                g.setColor(Color.BLACK);
                g.drawRect(16, 12, 4, 8);
            }
            case "terminal", "term", "xterm" -> {
                // CRT monitor with phosphor green prompt
                g.setColor(new Color(0x38, 0x38, 0x38)); // bezel
                g.fillRect(2, 3, 20, 15);
                g.setColor(Color.WHITE);
                g.drawRect(2, 3, 19, 14);
                g.setColor(Color.BLACK); // tube
                g.fillRect(4, 5, 16, 11);
                // Stand
                g.setColor(new Color(0x60, 0x60, 0x60));
                g.fillRect(10, 18, 4, 2);
                g.fillRect(7, 20, 10, 2);
                // >_ prompt
                g.setColor(new Color(0x33, 0xFF, 0x55));
                g.drawString(">", 5, 14);
                g.fillRect(11, 13, 4, 1);
            }
            case "lock" -> {
                // Brass padlock
                g.setColor(new Color(0xBB, 0xBB, 0xBB)); // shackle
                g.setStroke(new BasicStroke(2.0f));
                g.drawArc(7, 3, 10, 11, 0, 180);
                g.setStroke(new BasicStroke(1.0f));
                // Lock body
                g.setColor(new Color(0xD8, 0xA8, 0x30)); // brass
                g.fillRect(5, 9, 14, 12);
                g.setColor(new Color(0xF8, 0xC8, 0x50)); // highlight
                g.drawLine(6, 10, 17, 10);
                g.drawLine(6, 10, 6, 19);
                g.setColor(new Color(0x70, 0x50, 0x10)); // shadow
                g.drawRect(5, 9, 13, 11);
                // Keyhole
                g.setColor(Color.BLACK);
                g.fillOval(11, 12, 2, 2);
                g.fillRect(11, 14, 2, 4);
            }
            case "power", "exit" -> {
                // Power button
                g.setColor(new Color(0x40, 0x40, 0x40));
                g.fillOval(3, 3, 18, 18);
                g.setColor(new Color(0xD0, 0x30, 0x30)); // red ring
                g.setStroke(new BasicStroke(2.0f));
                g.drawArc(6, 6, 12, 12, 135, 270);
                g.drawLine(12, 5, 12, 12); // top vertical line
            }
            case "trash", "waste" -> {
                // Wastebasket
                g.setColor(new Color(0xA0, 0xA0, 0xA0));
                g.fillRect(6, 7, 12, 14);
                g.setColor(new Color(0x40, 0x40, 0x40));
                g.drawRect(6, 7, 12, 14);
                // Rim & Lid
                g.setColor(new Color(0xD0, 0xD0, 0xD0));
                g.fillRect(4, 5, 16, 3);
                g.setColor(Color.BLACK);
                g.drawRect(4, 5, 16, 3);
                // Ribs
                g.setColor(new Color(0x60, 0x60, 0x60));
                g.drawLine(9, 9, 9, 19);
                g.drawLine(12, 9, 12, 19);
                g.drawLine(15, 9, 15, 19);
            }
            case "diagnostics", "tools" -> {
                // Red toolbox
                g.setColor(new Color(0xB8, 0x38, 0x28)); // box
                g.fillRect(4, 8, 16, 12);
                g.setColor(Color.BLACK);
                g.drawRect(4, 8, 16, 12);
                // Handle
                g.setColor(new Color(0x50, 0x50, 0x50));
                g.drawRect(9, 4, 6, 4);
                // Latch
                g.setColor(new Color(0xE0, 0xE0, 0xE0));
                g.fillRect(10, 12, 4, 3);
                g.setColor(Color.BLACK);
                g.drawRect(10, 12, 4, 3);
            }
            default -> {
                // Workstation window
                g.setColor(new Color(0x60, 0x80, 0xA0));
                g.fillRect(3, 3, 18, 18);
                g.setColor(new Color(0xC0, 0xC0, 0xC0));
                g.fillRect(4, 4, 16, 4);
                g.setColor(Color.WHITE);
                g.fillRect(4, 9, 16, 11);
                g.setColor(Color.BLACK);
                g.drawRect(3, 3, 17, 17);
            }
        }

        g.dispose();
        return img;
    }

    public static void saveDefaultIconsToDirectory(File dir) {
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String[] keys = {"file-manager", "text-editor", "terminal", "lock", "power", "trash", "diagnostics", "window"};
        for (String key : keys) {
            File target = new File(dir, key + ".png");
            if (!target.exists()) {
                try {
                    BufferedImage img = generatePixmaps(key, 32);
                    ImageIO.write(img, "png", target);
                    LOGGER.info("Generated default theme icon: " + target.getAbsolutePath());
                } catch (Exception e) {
                    LOGGER.warning("Failed to save icon " + key + ": " + e.getMessage());
                }
            }
        }
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            for (String dir : args) {
                saveDefaultIconsToDirectory(new File(dir));
            }
        } else {
            saveDefaultIconsToDirectory(new File("src/main/resources/themes/classic/icons"));
            saveDefaultIconsToDirectory(new File("config/includes.chroot/etc/dazeos/themes/classic/icons"));
        }
    }
}
