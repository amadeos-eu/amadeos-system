package eu.amadeos.system.clipboard;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Offers a history entry on the clipboard in all the formats it has: files, image, HTML and text. AWT
 * turns them into the X formats programs ask for (text/uri-list, image/png, text/html, UTF8_STRING ...).
 */
final class ClipTransferable implements Transferable {
    private final ClipEntry entry;
    private final DataFlavor[] flavors;

    ClipTransferable(ClipEntry entry) {
        this.entry = entry;
        List<DataFlavor> list = new ArrayList<>();
        if (!entry.files().isEmpty()) list.add(DataFlavor.javaFileListFlavor);
        if (entry.image() != null) list.add(DataFlavor.imageFlavor);
        if (entry.html() != null) list.add(DataFlavor.allHtmlFlavor);
        if (entry.text() != null) list.add(DataFlavor.stringFlavor);
        this.flavors = list.toArray(DataFlavor[]::new);
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return flavors.clone();
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        for (DataFlavor f : flavors) {
            if (f.equals(flavor)) return true;
        }
        return false;
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
        if (DataFlavor.javaFileListFlavor.equals(flavor)) {
            List<File> files = new ArrayList<>();
            entry.files().forEach(p -> files.add(p.toFile()));
            return files;
        }
        if (DataFlavor.imageFlavor.equals(flavor)) return entry.image();
        if (DataFlavor.allHtmlFlavor.equals(flavor)) return entry.html();
        return entry.text();
    }
}
