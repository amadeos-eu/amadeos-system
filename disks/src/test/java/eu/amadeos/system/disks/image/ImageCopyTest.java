package eu.amadeos.system.disks.image;

import eu.amadeos.system.disks.FakeDisks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageCopyTest {
    static final String EMPTY_SHA = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    static byte[] data(int size) {
        byte[] b = new byte[size];
        new Random(7).nextBytes(b);
        return b;
    }

    @Test
    void copiesWithProgressAndChecksum() throws Exception {
        byte[] in = data(9 * 1024 * 1024 + 17);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Long> seen = new ArrayList<>();
        String sha = ImageCopy.copy(new ByteArrayInputStream(in), out, in.length, (d, t) -> seen.add(d), () -> false);
        assertArrayEquals(in, out.toByteArray());
        assertEquals(List.of(4L * 1024 * 1024, 8L * 1024 * 1024, (long) in.length), seen);
        assertEquals(sha, ImageCopy.sha256(new ByteArrayInputStream(in), -1, (d, t) -> { }, () -> false));
        assertEquals(EMPTY_SHA, ImageCopy.sha256(new ByteArrayInputStream(in), 0, (d, t) -> { }, () -> false));
    }

    @Test
    void stops() {
        assertThrows(ImageCopy.Stopped.class, () -> ImageCopy.copy(new ByteArrayInputStream(data(10)),
                OutputStream.nullOutputStream(), 10, (d, t) -> { }, () -> true));
    }

    @Test
    void findsThePublishedChecksum(@TempDir Path dir) throws Exception {
        Path iso = Files.write(dir.resolve("amadeos-1.1.0-amd64.iso"), new byte[0]);
        assertEquals(Optional.empty(), ImageCopy.publishedSha256(iso));
        Files.writeString(dir.resolve("SHA256SUMS"), "1111111111111111111111111111111111111111111111111111111111111111"
                + "  other.iso\n" + EMPTY_SHA.toUpperCase() + " *amadeos-1.1.0-amd64.iso\n");
        assertEquals(Optional.of(EMPTY_SHA), ImageCopy.publishedSha256(iso));
        Files.writeString(dir.resolve("amadeos-1.1.0-amd64.iso.sha256"), EMPTY_SHA + "\n");
        assertEquals(Optional.of(EMPTY_SHA), ImageCopy.publishedSha256(iso), "own file without name");
        assertEquals(Optional.empty(), ImageCopy.find(EMPTY_SHA + "  other.iso", "x.iso", false));
    }

    @Test
    void writesAndComparesThroughTheService(@TempDir Path dir) throws Exception {
        byte[] content = data(5 * 1024 * 1024);
        Path img = Files.write(dir.resolve("stick.img"), content);
        FakeDisks disks = new FakeDisks();
        var stick = disks.drive("/dev/sdb");
        assertTrue(ImageJobs.write(disks, stick, img, (p, d, t) -> { }, () -> false));
        assertEquals(content.length, ImageJobs.imageSize(img));

        Path xz = dir.resolve("stick.img.xz");
        try (OutputStream o = new XZOutputStream(Files.newOutputStream(xz), new LZMA2Options(1))) {
            o.write(content);
        }
        assertTrue(ImageCopy.isXz(xz));
        assertEquals(content.length, ImageJobs.imageSize(xz));
        assertTrue(ImageJobs.write(disks, stick, xz, (p, d, t) -> { }, () -> false));

        Path saved = dir.resolve("saved.img.xz");
        ImageJobs.save(disks, stick, saved, (p, d, t) -> { }, () -> false);
        assertEquals(content.length, ImageJobs.imageSize(saved));
        assertTrue(Files.list(dir).noneMatch(p -> p.getFileName().toString().endsWith(".part")));
    }
}
