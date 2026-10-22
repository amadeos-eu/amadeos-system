package eu.amadeos.system.clipboard;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads and offers the clipboard through AWT, which speaks the X11 selection protocol (conversions, large
 * transfers in pieces) and maps the X formats to Java's: text, HTML, images and file lists.
 */
public final class AwtClipboards implements SystemClipboards {
    private static final Logger LOGGER = Logger.getLogger(AwtClipboards.class.getName());
    /** Longer texts are not kept: a history entry is something to paste again, not a document. */
    static final int MAX_TEXT = 4 * 1024 * 1024;

    private final Clipboard clipboard;
    private final Clipboard primary;
    private final Supplier<Instant> clock;
    private final ClipboardOwner owner = (c, contents) -> {
    };

    public AwtClipboards(Supplier<Instant> clock) {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        this.clipboard = toolkit.getSystemClipboard();
        this.primary = toolkit.getSystemSelection();
        this.clock = clock;
    }

    @Override
    public Optional<ClipEntry> read(Selection selection) {
        Clipboard source = selection == Selection.PRIMARY ? primary : clipboard;
        if (source == null) return Optional.empty();
        try {
            Transferable contents = source.getContents(null);
            return contents == null ? Optional.empty() : entryOf(contents, clock.get());
        } catch (IllegalStateException e) {
            LOGGER.fine(() -> "Clipboard busy: " + e.getMessage());
            return Optional.empty();
        }
    }

    /** The entry for what a transferable offers, or nothing if it offers none of the kept formats. */
    static Optional<ClipEntry> entryOf(Transferable contents, Instant copied) {
        List<Path> files = files(contents);
        String text = text(contents);
        String html = html(contents);
        BufferedImage image = files.isEmpty() ? image(contents) : null;
        if (text == null && html == null && image == null && files.isEmpty()) return Optional.empty();
        return Optional.of(new ClipEntry(text, html, image, files, copied));
    }

    @Override
    public void own(ClipEntry entry) {
        clipboard.setContents(new ClipTransferable(entry), owner);
    }

    private static List<Path> files(Transferable contents) {
        if (!contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return List.of();
        Object data = data(contents, DataFlavor.javaFileListFlavor);
        if (!(data instanceof List<?> list)) return List.of();
        List<Path> files = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof File f) files.add(f.toPath());
        }
        return files;
    }

    private static String text(Transferable contents) {
        if (!contents.isDataFlavorSupported(DataFlavor.stringFlavor)) return null;
        return data(contents, DataFlavor.stringFlavor) instanceof String s && s.length() <= MAX_TEXT ? s : null;
    }

    /** HTML in whatever class AWT offers it: String, Reader or bytes. */
    private static String html(Transferable contents) {
        DataFlavor best = null;
        for (DataFlavor flavor : contents.getTransferDataFlavors()) {
            if (!"text".equals(flavor.getPrimaryType()) || !"html".equals(flavor.getSubType())) continue;
            if (best == null || flavor.getRepresentationClass() == String.class) best = flavor;
        }
        if (best == null) return null;
        Object data = data(contents, best);
        try {
            String html = switch (data) {
                case String s -> s;
                case Reader r -> {
                    try (r) {
                        StringBuilder sb = new StringBuilder();
                        char[] buffer = new char[8192];
                        for (int n; (n = r.read(buffer)) > 0 && sb.length() <= MAX_TEXT; ) sb.append(buffer, 0, n);
                        yield sb.toString();
                    }
                }
                case InputStream in -> {
                    try (in) {
                        yield new String(in.readNBytes(MAX_TEXT + 1), StandardCharsets.UTF_8);
                    }
                }
                case null, default -> null;
            };
            return html == null || html.length() > MAX_TEXT ? null : html;
        } catch (IOException e) {
            return null;
        }
    }

    private static BufferedImage image(Transferable contents) {
        if (!contents.isDataFlavorSupported(DataFlavor.imageFlavor)) return null;
        return data(contents, DataFlavor.imageFlavor) instanceof Image image ? toBufferedImage(image) : null;
    }

    static BufferedImage toBufferedImage(Image image) {
        int w = image.getWidth(null);
        int h = image.getHeight(null);
        if (w <= 0 || h <= 0) return null;
        BufferedImage copy = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return copy;
    }

    private static Object data(Transferable contents, DataFlavor flavor) {
        try {
            return contents.getTransferData(flavor);
        } catch (UnsupportedFlavorException | IOException | RuntimeException e) {
            LOGGER.log(Level.FINE, "Cannot read " + flavor.getMimeType(), e);
            return null;
        }
    }
}
