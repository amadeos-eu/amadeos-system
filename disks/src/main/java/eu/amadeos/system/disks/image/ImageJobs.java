package eu.amadeos.system.disks.image;

import eu.amadeos.system.disks.model.Drive;
import eu.amadeos.system.disks.service.DiskException;
import eu.amadeos.system.disks.service.DiskService;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.SeekableFileInputStream;
import org.tukaani.xz.SeekableXZInputStream;
import org.tukaani.xz.XZOutputStream;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.BooleanSupplier;

/** Writes an image file to a drive and saves a drive as an image file. */
public final class ImageJobs {

    /** What the window shows while it runs. */
    public enum Phase {
        WRITING, VERIFYING, READING
    }

    /** Told about the phase and the bytes done of total. */
    @FunctionalInterface
    public interface Listener {
        void progress(Phase phase, long done, long total);
    }

    private ImageJobs() {
    }

    /** The bytes an image puts on a drive: its size, or the unpacked size of an .xz. */
    public static long imageSize(Path image) throws IOException {
        if (ImageCopy.isXz(image)) {
            try (SeekableXZInputStream xz = new SeekableXZInputStream(new SeekableFileInputStream(image.toFile()))) {
                return xz.length();
            }
        }
        return Files.size(image);
    }

    private static InputStream openImage(Path image) throws IOException {
        if (ImageCopy.isXz(image)) {
            return new SeekableXZInputStream(new SeekableFileInputStream(image.toFile()));
        }
        return new BufferedInputStream(Files.newInputStream(image), ImageCopy.BLOCK);
    }

    // --8<-- [start:write]
    /**
     * Writes the image to the whole drive, then reads the written part back and compares the checksums.
     *
     * @return true when the read-back matches
     */
    public static boolean write(DiskService service, Drive drive, Path image, Listener listener, BooleanSupplier stop)
            throws IOException, DiskException {
        long total = imageSize(image);
        if (total > drive.size()) throw new IOException("image larger than the drive");
        String written;
        try (InputStream in = openImage(image); OutputStream out = service.openForRestore(drive)) {
            written = ImageCopy.copy(in, out, total, (d, t) -> listener.progress(Phase.WRITING, d, t), stop);
            if (out instanceof FileOutputStream f) f.getFD().sync();
        }
        String back;
        try (InputStream in = service.openForBackup(drive)) {
            back = ImageCopy.sha256(new BufferedInputStream(in, ImageCopy.BLOCK), total,
                    (d, t) -> listener.progress(Phase.VERIFYING, d, t), stop);
        }
        return written.equals(back);
    }
    // --8<-- [end:write]

    /**
     * Saves the whole drive into an image file, packed with xz when the name ends in .xz. Writes to a hidden file
     * next to the target first, so a stopped run leaves nothing half-done behind.
     */
    public static void save(DiskService service, Drive drive, Path target, Listener listener, BooleanSupplier stop)
            throws IOException, DiskException {
        Path part = target.resolveSibling("." + target.getFileName() + ".part");
        boolean xz = target.getFileName().toString().endsWith(".xz");
        try (InputStream in = service.openForBackup(drive)) {
            try (OutputStream file = Files.newOutputStream(part);
                 OutputStream out = xz ? new XZOutputStream(file, new LZMA2Options(3)) : file) {
                ImageCopy.copy(new BufferedInputStream(in, ImageCopy.BLOCK), out, drive.size(),
                        (d, t) -> listener.progress(Phase.READING, d, t), stop);
            }
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(part);
        }
    }
}
