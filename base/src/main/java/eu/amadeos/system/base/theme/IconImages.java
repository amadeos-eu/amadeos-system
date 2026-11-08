package eu.amadeos.system.base.theme;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.FloatSize;
import com.github.weisj.jsvg.view.ViewBox;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 * Finds and reads the icon files of an icon theme folder or {@code /usr/share/pixmaps}: PNG, SVG (drawn at the
 * requested size with jsvg) and XPM (older programs such as xterm, Python or OpenJDK ship only those).
 * The file names of each folder are read once and kept, so looking up many names in the hundreds of
 * folders of hicolor stays quick.
 */
final class IconImages {
    private static final Logger LOGGER = Logger.getLogger(IconImages.class.getName());

    /** Tried in this order for a name without extension. */
    private static final String[] EXTENSIONS = {".png", ".svg", ".xpm"};

    /** A folder's files by lower-case name, with the folder's modification time when they were read. */
    private record Listing(long modified, Map<String, File> files) {}

    /** Folder path to its listing; read again when the folder changes (a program installed by Software). */
    private static final Map<String, Listing> FOLDERS = new ConcurrentHashMap<>();

    private IconImages() {}

    static void clearCache() {
        FOLDERS.clear();
    }

    /** The icon {@code name} (without extension) in {@code dir}, or null when the folder has none. */
    static BufferedImage find(File dir, String name, int size) {
        if (dir == null || name == null || name.isBlank()) return null;
        Map<String, File> files = filesOf(dir);
        if (files.isEmpty()) return null;
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : EXTENSIONS) {
            File f = files.get(lower + ext);
            if (f != null) {
                BufferedImage img = read(f, size);
                if (img != null) return img;
            }
        }
        // A name given with its extension (Icon=foo.png)
        File exact = files.get(lower);
        return exact != null ? read(exact, size) : null;
    }

    private static Map<String, File> filesOf(File dir) {
        long modified = dir.lastModified(); // 0 when the folder does not exist
        Listing listing = FOLDERS.get(dir.getAbsolutePath());
        if (listing == null || listing.modified() != modified) {
            listing = new Listing(modified, list(dir));
            FOLDERS.put(dir.getAbsolutePath(), listing);
        }
        return listing.files();
    }

    private static Map<String, File> list(File dir) {
        File[] list = dir.listFiles();
        if (list == null || list.length == 0) return Map.of();
        Map<String, File> map = new HashMap<>();
        for (File f : list) {
            // The exact spelling wins over another file that differs only in case.
            map.merge(f.getName().toLowerCase(Locale.ROOT), f,
                    (old, cur) -> cur.getName().equals(cur.getName().toLowerCase(Locale.ROOT)) ? cur : old);
        }
        return map;
    }

    /** Reads a PNG/JPEG/GIF, SVG or XPM file; SVG is drawn at {@code size} pixels. Null when it cannot be read. */
    static BufferedImage read(File file, int size) {
        if (file == null || !file.isFile()) return null;
        String n = file.getName().toLowerCase(Locale.ROOT);
        try {
            if (n.endsWith(".svg") || n.endsWith(".svgz")) {
                return renderSvg(file, size);
            }
            if (n.endsWith(".xpm")) {
                return XpmReader.read(file.toPath());
            }
            return ImageIO.read(file);
        } catch (Exception | LinkageError e) {
            LOGGER.log(Level.FINE, "Cannot read icon " + file, e);
            return null;
        }
    }

    static BufferedImage renderSvg(File file, int size) throws Exception {
        SVGDocument doc = new SVGLoader().load(file.toURI().toURL());
        if (doc == null) return null;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            // Keep the aspect ratio of a non-square drawing and centre it.
            FloatSize s = doc.size();
            float w = size;
            float h = size;
            if (s.width > 0 && s.height > 0 && s.width != s.height) {
                if (s.width > s.height) h = size * s.height / s.width;
                else w = size * s.width / s.height;
            }
            doc.render(null, g, new ViewBox((size - w) / 2f, (size - h) / 2f, w, h));
        } finally {
            g.dispose();
        }
        return img;
    }
}
