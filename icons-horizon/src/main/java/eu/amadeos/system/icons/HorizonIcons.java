package eu.amadeos.system.icons;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The Horizon icon theme of amadeOS, {@code amadeos-horizon}: original artwork drawn in Java2D, written as PNG
 * files in the freedesktop.org icon theme layout. What it does not have, it takes from NsCDE and hicolor
 * ({@code Inherits} in {@code index.theme}).
 *
 * <p>{@code main} writes the theme into a folder: {@code HorizonIcons <folder>} makes
 * {@code <folder>/index.theme} and {@code <folder>/<size>x<size>/<context>/<name>.png}.
 */
public final class HorizonIcons {
    public static final String THEME = "amadeos-horizon";
    public static final int[] SIZES = {16, 22, 24, 32, 48, 64, 128, 256};
    public static final String[] CONTEXTS = {"apps", "categories", "places", "mimetypes", "devices"};

    /** The kind of picture. */
    public enum Kind { TILE, FOLDER, PAGE }

    /**
     * One icon.
     *
     * @param context the folder of the theme: apps, places, ...
     * @param names   the file names; the first one is its own, the others are other programs' names for it
     */
    public record Entry(String context, List<String> names, Kind kind, Color top, Color bottom, Consumer<Pen> glyph) {
        public BufferedImage paint(int size) {
            return switch (kind) {
                case TILE -> IconPainter.tile(size, top, bottom, glyph);
                case FOLDER -> IconPainter.folder(size, top, bottom, glyph);
                case PAGE -> IconPainter.page(size, top, glyph);
            };
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private static Color c(int rgb) {
        return new Color(rgb);
    }

    private static void app(String names, int top, int bottom, Consumer<Pen> glyph) {
        ENTRIES.add(new Entry("apps", List.of(names.split(" ")), Kind.TILE, c(top), c(bottom), glyph));
    }

    private static void category(String names, int top, int bottom, Consumer<Pen> glyph) {
        ENTRIES.add(new Entry("categories", List.of(names.split(" ")), Kind.TILE, c(top), c(bottom), glyph));
    }

    private static void place(String names, Consumer<Pen> glyph) {
        ENTRIES.add(new Entry("places", List.of(names.split(" ")), Kind.FOLDER, IconPainter.FOLDER_TOP,
                IconPainter.FOLDER_BOTTOM, glyph));
    }

    private static void device(String names, int top, int bottom, Consumer<Pen> glyph) {
        ENTRIES.add(new Entry("devices", List.of(names.split(" ")), Kind.TILE, c(top), c(bottom), glyph));
    }

    private static void type(String names, int band, Consumer<Pen> glyph) {
        ENTRIES.add(new Entry("mimetypes", List.of(names.split(" ")), Kind.PAGE, c(band), c(band), glyph));
    }

    // ---- glyphs shared by several icons ----------------------------------------------------------

    static void folderGlyph(Pen p) {
        p.path("M 30 46 L 30 86 Q 30 92 36 92 L 92 92 Q 98 92 98 86 L 98 52 Q 98 46 92 46 L 64 46 L 56 38 L 36 38"
                + " Q 30 38 30 44 Z");
        p.line(30, 56, 98, 56);
    }

    static void documentGlyph(Pen p) {
        p.path("M 40 30 L 72 30 L 88 46 L 88 90 Q 88 96 82 96 L 40 96 Q 34 96 34 90 L 34 36 Q 34 30 40 30 Z");
        p.path("M 72 30 L 72 46 L 88 46");
    }

    static void pictureGlyph(Pen p) {
        p.rect(28, 34, 72, 60, 8);
        p.poly(false, 34, 86, 54, 64, 68, 78, 78, 68, 96, 86);
        p.dot(80, 52, 7);
    }

    static void noteGlyph(Pen p) {
        p.path("M 54 84 L 54 34 L 88 26 L 88 76");
        p.dot(46, 86, 9);
        p.dot(80, 78, 9);
    }

    static void playGlyph(Pen p) {
        p.fillPoly(54, 44, 84, 64, 54, 84);
        p.poly(true, 54, 44, 84, 64, 54, 84);
    }

    static void gear(Pen p) {
        for (int a = 0; a < 360; a += 45) {
            double r = Math.toRadians(a);
            double x = 64 + Math.cos(r) * 30, y = 64 + Math.sin(r) * 30;
            p.line(64 + Math.cos(r) * 22, 64 + Math.sin(r) * 22, x, y);
        }
        p.circle(64, 64, 22);
        p.circle(64, 64, 9);
    }

    static void person(Pen p, double cx, double cy, double scale) {
        p.circle(cx, cy - 14 * scale, 12 * scale);
        p.path("M " + (cx - 24 * scale) + " " + (cy + 26 * scale) + " Q " + (cx - 24 * scale) + " " + (cy + 4 * scale)
                + " " + cx + " " + (cy + 4 * scale) + " Q " + (cx + 24 * scale) + " " + (cy + 4 * scale) + " "
                + (cx + 24 * scale) + " " + (cy + 26 * scale));
    }

    static {
        // ---- programs ----------------------------------------------------------------------------
        app("amade-files system-file-manager file-manager", 0x3FC7BF, 0x1F8C8F, HorizonIcons::folderGlyph);
        app("amade-pad accessories-text-editor text-editor", 0xF2C14E, 0xC98A1E, p -> {
            p.rect(38, 30, 52, 66, 8);
            p.line(48, 50, 80, 50);
            p.line(48, 62, 80, 62);
            p.line(48, 74, 68, 74);
        });
        app("amade-writepad x-office-document-template libreoffice-writer-template", 0x6E8BF0, 0x3B54C4, p -> {
            documentGlyph(p);
            p.line(46, 64, 74, 64);
            p.line(46, 78, 66, 78);
        });
        app("amade-mail mail internet-mail evolution", 0xF08A5D, 0xC4512E, p -> {
            p.rect(28, 38, 72, 52, 8);
            p.poly(false, 30, 42, 64, 68, 98, 42);
        });
        app("amade-calendar office-calendar calendar", 0x9B7BEA, 0x6343C2, p -> {
            p.rect(30, 36, 68, 60, 9);
            p.line(30, 54, 98, 54);
            p.line(46, 28, 46, 42);
            p.line(82, 28, 82, 42);
            p.dot(64, 74, 8);
        });
        app("amade-calculator accessories-calculator calculator", 0x7A8597, 0x4A5366, p -> {
            p.rect(36, 26, 56, 76, 9);
            p.fillRect(45, 36, 38, 14, 3);
            for (int r = 0; r < 2; r++) for (int col = 0; col < 3; col++) p.dot(50 + 14 * col, 66 + 14 * r, 4);
        });
        app("amade-contacts x-office-address-book address-book", 0xE8A23C, 0xB86A14, p -> {
            p.rect(30, 28, 64, 72, 9);
            person(p, 62, 62, 0.85);
            p.line(98, 42, 98, 52);
            p.line(98, 64, 98, 74);
        });
        app("amade-web web-browser internet-web-browser", 0x4FA8F0, 0x2463C2, p -> {
            p.circle(64, 64, 34);
            p.path("M 64 30 Q 44 64 64 98 Q 84 64 64 30");
            p.line(32, 54, 96, 54);
            p.line(32, 74, 96, 74);
        });
        app("amade-news news-feed application-rss+xml", 0xEE6B5A, 0xB43A3A, p -> {
            p.rect(28, 32, 72, 64, 8);
            p.line(38, 46, 90, 46);
            p.fillRect(38, 56, 22, 18, 3);
            p.line(68, 58, 90, 58);
            p.line(68, 70, 90, 70);
            p.line(38, 84, 90, 84);
        });
        app("amade-audio multimedia-audio-player audio-player", 0xE2557A, 0xA62950, HorizonIcons::noteGlyph);
        app("amade-video multimedia-video-player video-player", 0xA45BE0, 0x6A2BB0, p -> {
            p.rect(28, 36, 72, 56, 8);
            playGlyph(p);
        });
        app("amade-tv tv video-television", 0x3E9BE0, 0x1D61A8, p -> {
            p.rect(26, 36, 76, 50, 8);
            p.line(48, 98, 80, 98);
            p.fillPoly(58, 50, 76, 61, 58, 72);
        });
        app("amade-imageviewer image-viewer eog", 0x45C2E0, 0x1F86B5, HorizonIcons::pictureGlyph);
        app("amade-imageeditor applications-graphics-editor gimp", 0xF0874E, 0xC2462A, p -> {
            p.rect(26, 38, 58, 50, 7);
            p.poly(false, 32, 82, 46, 66, 56, 76);
            p.path("M 98 30 L 70 70");
            p.width(10).line(98, 30, 86, 47);
            p.width(6).dot(68, 74, 6);
        });
        app("amade-pdfviewer application-pdf-viewer evince", 0xE65A5A, 0xA82B36, p -> {
            documentGlyph(p);
            p.fillRect(44, 60, 34, 22, 4);
        });
        app("amade-books accessories-dictionary e-book-reader", 0xC98A4E, 0x8E5428, p -> {
            p.path("M 64 40 Q 50 30 28 32 L 28 90 Q 50 88 64 98 Q 78 88 100 90 L 100 32 Q 78 30 64 40 Z");
            p.line(64, 40, 64, 98);
        });
        app("amade-fractals", 0x4B5BD6, 0x2B2F8F, p -> {
            p.poly(true, 64, 28, 102, 96, 26, 96);
            p.poly(true, 45, 62, 83, 62, 64, 96);
            p.poly(true, 54.5, 45, 73.5, 45, 64, 62);
        });
        app("amade-imagescan scanner xsane", 0x5FB0A0, 0x2F7A6E, p -> {
            p.rect(26, 62, 76, 30, 7);
            p.line(36, 77, 70, 77);
            p.dot(88, 77, 4);
            p.path("M 40 62 L 40 30 L 78 30 L 88 40 L 88 62");
        });
        app("amade-screenshot applets-screenshooter screenshot", 0x5C6BC0, 0x34408A, p -> {
            p.poly(false, 28, 46, 28, 32, 42, 32);
            p.poly(false, 86, 32, 100, 32, 100, 46);
            p.poly(false, 100, 82, 100, 96, 86, 96);
            p.poly(false, 42, 96, 28, 96, 28, 82);
            p.circle(64, 64, 16);
            p.dot(64, 64, 5);
        });
        app("amade-backup deja-dup backup", 0x3BAF8E, 0x1E7A62, p -> {
            p.arc(64, 64, 32, 120, 300);
            p.poly(false, 38, 34, 44, 50, 58, 42);
            p.poly(false, 64, 48, 64, 66, 76, 74);
        });
        app("amade-webcam camera-web cheese", 0x6C7A89, 0x3E4A58, p -> {
            p.circle(64, 58, 28);
            p.circle(64, 58, 12);
            p.dot(64, 58, 4);
            p.path("M 46 98 L 82 98 M 64 86 L 64 98");
        });
        app("amade-terminal utilities-terminal terminal org.gnome.Terminal", 0x3A3F4E, 0x1C1F29, p -> {
            p.poly(false, 38, 48, 54, 62, 38, 76);
            p.line(62, 80, 90, 80);
        });
        app("amadeos-settings preferences-desktop preferences-system style preferences-desktop-theme",
                0x8C96A8, 0x576074, HorizonIcons::gear);
        app("amadeos-software system-software-install software-store gnome-software system-application-manager",
                0x4FB874, 0x22844A, p -> {
                    p.poly(true, 64, 28, 96, 44, 96, 84, 64, 100, 32, 84, 32, 44);
                    p.poly(false, 32, 44, 64, 60, 96, 44);
                    p.line(64, 60, 64, 100);
                });
        app("amadeos-taskmanager utilities-system-monitor system-monitor", 0x47B39D, 0x23786A, p -> {
            p.rect(26, 32, 76, 64, 8);
            p.poly(false, 34, 70, 48, 70, 56, 50, 66, 84, 76, 60, 82, 70, 94, 70);
        });
        app("amadeos-sysinfo computer", 0x5E86C9, 0x355A99, p -> {
            p.rect(26, 32, 76, 52, 8);
            p.line(50, 98, 78, 98);
            p.line(64, 84, 64, 98);
            p.dot(64, 46, 4);
            p.line(64, 56, 64, 72);
        });
        app("amadeos-print printer document-print", 0x7F8C9D, 0x4D5868, p -> {
            p.rect(26, 50, 76, 32, 7);
            p.path("M 42 50 L 42 30 L 86 30 L 86 50");
            p.path("M 42 72 L 42 98 L 86 98 L 86 72");
            p.dot(90, 60, 3.5);
        });
        app("amadeos-logviewer utilities-log-viewer logviewer", 0x8F7A5E, 0x5C4B34, p -> {
            p.rect(32, 28, 64, 72, 8);
            for (int y = 44; y <= 84; y += 13) {
                p.dot(44, y, 3);
                p.line(54, y, 86, y);
            }
        });
        app("help-browser system-help amadeos-handbook", 0x4CA3E0, 0x2569A8, p -> {
            p.circle(64, 64, 34);
            p.path("M 52 54 Q 52 40 64 40 Q 76 40 76 52 Q 76 60 64 64 L 64 72");
            p.dot(64, 84, 4.5);
        });
        app("system-lock-screen", 0x6D7891, 0x434C63, p -> {
            p.rect(36, 58, 56, 40, 8);
            p.path("M 46 58 L 46 46 Q 46 30 64 30 Q 82 30 82 46 L 82 58");
            p.dot(64, 77, 5);
        });
        app("system-shutdown system-log-out application-exit", 0xE0605A, 0xA83234, p -> {
            p.arc(64, 66, 30, 125, 290);
            p.line(64, 30, 64, 62);
        });

        // ---- games -------------------------------------------------------------------------------
        app("amade-sands-game", 0xE6B45A, 0xB0742A, p -> {
            p.path("M 26 84 Q 46 66 64 80 Q 82 94 102 76");
            p.path("M 26 98 Q 50 86 70 96 Q 86 104 102 94");
            p.circle(82, 44, 12);
        });
        app("amade-mines-game", 0x8A96A6, 0x56606E, p -> {
            p.dot(64, 66, 20);
            for (int a = 0; a < 360; a += 45) {
                double r = Math.toRadians(a);
                p.line(64 + Math.cos(r) * 22, 66 + Math.sin(r) * 22, 64 + Math.cos(r) * 32, 66 + Math.sin(r) * 32);
            }
        });
        app("amade-town-game", 0x5CB6E6, 0x2C79B8, p -> {
            p.poly(false, 24, 98, 24, 60, 42, 46, 60, 60, 60, 98);
            p.rect(66, 38, 22, 60, 3);
            p.poly(false, 88, 98, 88, 66, 104, 66, 104, 98);
            p.line(20, 98, 108, 98);
        });
        app("amade-templar-game", 0x8E6FD0, 0x523A94, p -> {
            p.path("M 64 28 L 96 38 L 96 62 Q 96 88 64 102 Q 32 88 32 62 L 32 38 Z");
            p.line(64, 44, 64, 86);
            p.line(48, 60, 80, 60);
        });
        app("amade-rivet-game", 0x5F6B7A, 0x2F3742, p -> {
            p.poly(true, 64, 28, 95, 46, 95, 82, 64, 100, 33, 82, 33, 46);
            p.circle(64, 64, 13);
        });
        app("amade-artillery-game", 0x7DA05A, 0x4A6E2E, p -> {
            p.path("M 30 92 Q 60 20 98 70");
            p.dot(98, 74, 6);
            p.rect(24, 90, 20, 10, 3);
        });
        app("amade-bricks-game", 0xE07A4A, 0xA84222, p -> {
            for (int col = 0; col < 3; col++) p.fillRect(26 + col * 26, 28, 22, 11, 3);
            for (int col = 0; col < 2; col++) p.fillRect(39 + col * 26, 44, 22, 11, 3);
            p.fillRect(26, 44, 9, 11, 3);
            p.fillRect(93, 44, 9, 11, 3);
            p.dot(72, 78, 6);
            p.width(8).line(46, 98, 82, 98);
        });
        app("amade-colony-game", 0x6CC07A, 0x2F8A48, p -> {
            p.poly(false, 32, 98, 32, 64, 54, 46, 76, 64, 76, 98);
            p.line(24, 98, 104, 98);
            p.circle(90, 60, 12);
            p.line(90, 72, 90, 98);
        });
        app("amade-hover-cab-game", 0xF2C94C, 0xC8901A, p -> {
            p.path("M 24 76 Q 24 64 36 62 L 48 46 Q 52 42 58 42 L 78 42 Q 86 42 90 50 L 98 62 Q 104 64 104 72"
                    + " L 104 76 Z");
            p.line(62, 44, 62, 62);
            p.line(34, 90, 46, 90);
            p.line(58, 90, 70, 90);
            p.line(82, 90, 94, 90);
            p.line(44, 100, 52, 100);
            p.line(76, 100, 84, 100);
        });
        app("amade-paddle-game", 0x4CC1B2, 0x1E8478, p -> {
            p.width(9).line(34, 42, 34, 70);
            p.line(94, 58, 94, 86);
            p.width(6).dot(64, 60, 6);
            p.line(64, 28, 64, 34);
            p.line(64, 94, 64, 100);
        });
        app("amade-rocks-game", 0x47506A, 0x232838, p -> {
            p.poly(true, 66, 30, 90, 36, 100, 58, 88, 80, 62, 78, 52, 56);
            p.fillPoly(30, 100, 40, 76, 50, 100);
        });
        app("amade-swarm-game", 0x3B4A86, 0x1C2350, p -> {
            for (int i = 0; i < 3; i++) {
                double x = 36 + i * 28;
                p.poly(false, x - 9, 44, x, 52, x + 9, 44);
                p.poly(false, x - 9, 58, x, 66, x + 9, 58);
            }
            p.poly(true, 64, 84, 74, 98, 54, 98);
        });
        app("amade-solitaire-game", 0x3FA36A, 0x1F6E40, p -> {
            p.rect(30, 34, 40, 56, 6);
            p.rect(58, 40, 40, 56, 6);
            p.path("M 78 58 Q 70 66 78 78 Q 86 66 78 58 Z");
        });
        app("amade-maze-game", 0x9A6ED6, 0x5E3A9E, p -> {
            p.rect(28, 28, 72, 72, 6);
            p.poly(false, 28, 46, 64, 46, 64, 64);
            p.poly(false, 46, 100, 46, 64);
            p.poly(false, 82, 28, 82, 82, 64, 82);
        });
        app("amade-jump-game", 0x56B8E8, 0x2A7CC0, p -> {
            p.line(26, 98, 54, 98);
            p.line(70, 76, 100, 76);
            p.path("M 40 90 Q 56 36 84 66");
            p.dot(40, 82, 6);
        });
        app("amade-snake-game", 0x7CC24A, 0x3F8A1E, p -> {
            p.path("M 30 92 Q 30 70 50 70 L 74 70 Q 96 70 96 50 Q 96 32 76 32 L 56 32");
            p.dot(52, 32, 7);
            p.dot(86, 92, 5);
        });
        app("amade-towers-game", 0xB08A5E, 0x725232, p -> {
            p.path("M 42 100 L 46 50 L 82 50 L 86 100 Z");
            p.poly(false, 40, 50, 40, 34, 52, 34, 52, 42, 58, 42, 58, 34, 70, 34, 70, 42, 76, 42, 76, 34, 88, 34, 88, 50);
            p.rect(58, 76, 12, 24, 5);
        });
        app("amade-blast-game", 0xE9663E, 0xA82E1E, p -> {
            p.circle(58, 72, 26);
            p.path("M 76 52 Q 84 40 94 40");
            p.line(98, 30, 98, 34);
            p.line(104, 40, 108, 40);
            p.line(102, 34, 105, 31);
        });

        // ---- the groups of programs (drawers) ----------------------------------------------------
        category("applications-office amadeos-office", 0x6E8BF0, 0x3B54C4, p -> {
            documentGlyph(p);
            p.line(46, 64, 74, 64);
        });
        category("applications-internet amadeos-internet", 0x4FA8F0, 0x2463C2, p -> {
            p.circle(64, 64, 34);
            p.path("M 64 30 Q 44 64 64 98 Q 84 64 64 30");
            p.line(32, 64, 96, 64);
        });
        category("applications-multimedia amadeos-multimedia", 0xE2557A, 0xA62950, HorizonIcons::noteGlyph);
        category("applications-graphics amadeos-graphics", 0x45C2E0, 0x1F86B5, HorizonIcons::pictureGlyph);
        category("applications-games amadeos-games input-gaming", 0x7CC24A, 0x3F8A1E, p -> {
            p.path("M 40 46 L 88 46 Q 104 46 104 66 Q 104 90 90 90 Q 82 90 76 80 L 52 80 Q 46 90 38 90 Q 24 90 24 66"
                    + " Q 24 46 40 46 Z");
            p.line(42, 58, 42, 72);
            p.line(35, 65, 49, 65);
            p.dot(82, 60, 4);
            p.dot(90, 70, 4);
        });
        category("applications-utilities applications-accessories amadeos-tools", 0x8C96A8, 0x576074, p -> {
            p.path("M 84 30 Q 70 30 66 42 Q 64 50 68 56 L 34 90 Q 30 96 36 100 Q 42 102 46 96 L 80 62"
                    + " Q 92 66 98 54 L 86 50 L 82 40 Z");
        });
        category("applications-system amadeos-system", 0x5E86C9, 0x355A99, HorizonIcons::gear);
        category("applications-other", 0x7A8597, 0x4A5366, p -> {
            for (int r = 0; r < 2; r++) for (int col = 0; col < 2; col++) p.rect(34 + 34 * col, 34 + 34 * r, 26, 26, 6);
        });

        // ---- places ------------------------------------------------------------------------------
        place("folder inode-directory folder-open", null);
        place("user-home folder-home", p -> {
            p.poly(false, 30, 64, 64, 34, 98, 64);
            p.poly(false, 40, 56, 40, 96, 88, 96, 88, 56);
        });
        place("folder-documents", p -> {
            p.rect(38, 28, 52, 72, 6);
            p.line(50, 48, 78, 48);
            p.line(50, 64, 78, 64);
            p.line(50, 80, 70, 80);
        });
        place("folder-download", p -> {
            p.line(64, 28, 64, 80);
            p.poly(false, 40, 58, 64, 82, 88, 58);
            p.line(34, 100, 94, 100);
        });
        place("folder-pictures folder-images", HorizonIcons::pictureGlyph);
        place("folder-music folder-sound", HorizonIcons::noteGlyph);
        place("folder-videos folder-video", p -> {
            p.rect(28, 36, 72, 56, 8);
            playGlyph(p);
        });
        place("user-desktop folder-desktop", p -> {
            p.rect(28, 34, 72, 50, 8);
            p.line(50, 98, 78, 98);
            p.line(64, 84, 64, 98);
        });
        place("folder-templates", p -> {
            p.rect(36, 30, 56, 68, 6);
            p.line(36, 50, 92, 50);
        });
        place("folder-publicshare folder-public", p -> person(p, 64, 64, 1.2));
        place("folder-remote network-workgroup network-server folder-network", p -> {
            p.circle(64, 60, 30);
            p.path("M 64 30 Q 46 60 64 90 Q 82 60 64 30");
            p.line(34, 60, 94, 60);
        });
        ENTRIES.add(new Entry("places", List.of("user-trash user-trash-empty trash-empty".split(" ")), Kind.TILE,
                c(0x9AA3B2), c(0x606A7C), p -> {
                    p.line(32, 40, 96, 40);
                    p.path("M 54 40 L 56 30 L 72 30 L 74 40");
                    p.path("M 38 40 L 44 98 L 84 98 L 90 40");
                    p.line(56, 54, 57, 84);
                    p.line(72, 54, 71, 84);
                }));
        ENTRIES.add(new Entry("places", List.of("user-trash-full trash-full".split(" ")), Kind.TILE,
                c(0x9AA3B2), c(0x606A7C), p -> {
                    p.line(32, 40, 96, 40);
                    p.path("M 54 40 L 56 30 L 72 30 L 74 40");
                    p.path("M 38 40 L 44 98 L 84 98 L 90 40");
                    p.fillPoly(46, 52, 82, 52, 80, 88, 48, 88);
                }));

        // ---- devices -----------------------------------------------------------------------------
        device("drive-harddisk drive-harddisk-system", 0x8C96A8, 0x576074, p -> {
            p.rect(26, 46, 76, 36, 8);
            p.dot(86, 64, 4);
            p.line(38, 64, 66, 64);
        });
        device("drive-removable-media media-removable drive-removable-media-usb", 0x5E86C9, 0x355A99, p -> {
            p.rect(42, 46, 44, 56, 8);
            p.path("M 50 46 L 50 28 L 78 28 L 78 46");
            p.line(58, 36, 58, 38);
            p.line(70, 36, 70, 38);
        });
        device("media-optical drive-optical", 0xA0A8B8, 0x6A7282, p -> {
            p.circle(64, 64, 34);
            p.circle(64, 64, 9);
        });

        // ---- file types --------------------------------------------------------------------------
        type("text-x-generic text-plain", 0x8C96A8, p -> {
            p.line(36, 52, 92, 52);
            p.line(36, 66, 92, 66);
            p.line(36, 80, 72, 80);
        });
        type("x-office-document application-vnd.oasis.opendocument.text application-msword", 0x4A6EE0, p -> {
            p.line(36, 52, 92, 52);
            p.line(36, 66, 92, 66);
            p.line(36, 80, 72, 80);
        });
        type("x-office-spreadsheet application-vnd.oasis.opendocument.spreadsheet", 0x3FA36A, p -> {
            p.rect(30, 40, 68, 48, 4);
            p.line(30, 56, 98, 56);
            p.line(30, 72, 98, 72);
            p.line(54, 40, 54, 88);
            p.line(76, 40, 76, 88);
        });
        type("x-office-presentation application-vnd.oasis.opendocument.presentation", 0xE8833A, p -> {
            p.rect(28, 38, 72, 46, 6);
            p.line(64, 84, 64, 98);
            p.line(48, 98, 80, 98);
        });
        type("image-x-generic image-png image-jpeg", 0x2FA7C9, HorizonIcons::pictureGlyph);
        type("audio-x-generic audio-mpeg audio-x-vorbis+ogg", 0xD94A70, HorizonIcons::noteGlyph);
        type("video-x-generic video-mp4 video-webm", 0x8E4BD0, HorizonIcons::playGlyph);
        type("application-pdf", 0xD84343, p -> {
            p.rect(30, 38, 68, 52, 6);
            p.fillRect(38, 46, 22, 20, 3);
            p.line(68, 50, 90, 50);
            p.line(68, 62, 90, 62);
            p.line(38, 78, 90, 78);
        });
        type("package-x-generic application-x-archive application-zip application-x-compressed-tar"
                + " application-x-tar application-gzip application-x-7z-compressed", 0xB08A5E, p -> {
            p.rect(30, 40, 68, 56, 6);
            p.line(30, 56, 98, 56);
            p.line(56, 70, 72, 70);
        });
        type("text-html application-xhtml+xml", 0x3E8EE0, p -> {
            p.poly(false, 48, 46, 30, 64, 48, 82);
            p.poly(false, 80, 46, 98, 64, 80, 82);
        });
        type("text-x-script application-x-shellscript text-x-java text-x-python", 0x3A3F4E, p -> {
            p.poly(false, 36, 48, 52, 62, 36, 76);
            p.line(60, 80, 90, 80);
        });
        type("application-x-executable application-x-deb application-vnd.debian.binary-package", 0x4FB874, p -> {
            p.poly(true, 64, 30, 94, 46, 94, 82, 64, 98, 34, 82, 34, 46);
            p.poly(false, 34, 46, 64, 62, 94, 46);
            p.line(64, 62, 64, 98);
        });
        type("application-x-generic unknown application-octet-stream", 0x9AA3B2, p -> p.dot(64, 64, 10));
        type("text-calendar", 0x7B5BD6, p -> {
            p.rect(32, 40, 64, 56, 7);
            p.line(32, 56, 96, 56);
            p.dot(64, 76, 7);
        });
        type("text-vcard text-x-vcard", 0xE8A23C, p -> person(p, 64, 64, 1.2));
        type("application-epub+zip application-x-cbz", 0xC98A4E, p -> {
            p.path("M 64 40 Q 50 30 28 32 L 28 90 Q 50 88 64 98 Q 78 88 100 90 L 100 32 Q 78 30 64 40 Z");
            p.line(64, 40, 64, 98);
        });
    }

    private HorizonIcons() {
    }

    public static List<Entry> entries() {
        return List.copyOf(ENTRIES);
    }

    /** Every name the theme has, for tests and the documentation. */
    public static Set<String> names() {
        Set<String> names = new LinkedHashSet<>();
        for (Entry e : ENTRIES) names.addAll(e.names());
        return names;
    }

    /** The {@code index.theme} of the theme. */
    public static String indexTheme() {
        StringBuilder dirs = new StringBuilder();
        StringBuilder sections = new StringBuilder();
        for (int size : SIZES) {
            for (String context : CONTEXTS) {
                String dir = size + "x" + size + "/" + context;
                if (!dirs.isEmpty()) dirs.append(',');
                dirs.append(dir);
                sections.append("\n[").append(dir).append("]\nSize=").append(size).append("\nContext=")
                        .append(Character.toUpperCase(context.charAt(0))).append(context.substring(1))
                        .append("\nType=Fixed\n");
            }
        }
        return "[Icon Theme]\nName=amadeOS Horizon\nComment=The icons of the Horizon desktop look of amadeOS\n"
                + "Inherits=NsCDE,hicolor\nExample=folder\nDirectories=" + dirs + "\n" + sections;
    }

    /** Writes the whole theme into {@code dir}. */
    public static int write(Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("index.theme"), indexTheme());
        int files = 0;
        for (int size : SIZES) {
            for (Entry e : ENTRIES) {
                Path folder = dir.resolve(size + "x" + size).resolve(e.context());
                Files.createDirectories(folder);
                ImageIO.write(e.paint(size), "png", folder.resolve(e.names().getFirst() + ".png").toFile());
                files++;
                // the other names of the icon point to the same file
                for (String name : e.names().subList(1, e.names().size())) {
                    Path link = folder.resolve(name + ".png");
                    Files.deleteIfExists(link);
                    Files.createSymbolicLink(link, Path.of(e.names().getFirst() + ".png"));
                    files++;
                }
            }
        }
        return files;
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: HorizonIcons <folder>");
            System.exit(2);
        }
        int files = write(Path.of(args[0]));
        System.out.println("[icons] " + files + " pictures of " + ENTRIES.size() + " icons in " + args[0]);
    }
}
