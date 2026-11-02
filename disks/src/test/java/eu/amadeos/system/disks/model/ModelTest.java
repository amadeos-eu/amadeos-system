package eu.amadeos.system.disks.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelTest {
    static Volume part(int n, long offset, long size, String type, String... mounts) {
        return new Volume("/p" + n, "/dev/sda" + n, n, offset, size, "filesystem", type, "", "", "", "",
                List.of(mounts), -1, false, null, false);
    }

    @Test
    void sizesAsOnTheBox() {
        assertEquals("32 GB", Sizes.bytes(32_015_679_488L, Locale.ENGLISH));
        assertEquals("1.0 TB", Sizes.bytes(1_000_204_886_016L, Locale.ENGLISH));
        assertEquals("4.1 GB", Sizes.bytes(4_100_000_000L, Locale.ENGLISH));
        assertEquals("4,1 GB", Sizes.bytes(4_100_000_000L, Locale.GERMAN));
        assertEquals("512 B", Sizes.bytes(512, Locale.ENGLISH));
        assertEquals("–", Sizes.bytes(-1, Locale.ENGLISH));
        assertEquals(13, Sizes.percent(4, 31));
    }

    @Test
    void freeSpaceBetweenAndAfterPartitions() {
        long gb = Sizes.GB;
        List<Segment> map = Layout.segments(100 * gb, "gpt",
                List.of(part(2, 50 * gb, 10 * gb, "ext4"), part(1, Sizes.MIB, 20 * gb, "vfat")), List.of(), null);
        assertEquals(4, map.size());
        assertEquals("/p1", map.get(0).volume().id());
        assertTrue(map.get(1).free());
        assertEquals(50 * gb - 20 * gb - Sizes.MIB, map.get(1).size());
        assertEquals("/p2", map.get(2).volume().id());
        assertTrue(map.get(3).free());
        assertEquals(100 * gb - Sizes.MIB - 60 * gb, map.get(3).size(), "GPT keeps 1 MiB at the end");
    }

    @Test
    void driveWithoutTable() {
        assertTrue(Layout.segments(8 * Sizes.GB, "", List.of(), List.of(), null).get(0).free());
        Volume whole = part(0, 0, 8 * Sizes.GB, "vfat");
        assertEquals(whole, Layout.segments(8 * Sizes.GB, "", List.of(), List.of(), whole).get(0).volume());
        assertEquals(List.of(), Layout.segments(0, "", List.of(), List.of(), null));
    }

    @Test
    void logicalPartitionsReplaceTheirContainer() {
        long gb = Sizes.GB;
        Volume ext = part(2, 10 * gb, 20 * gb, "extended");
        Volume l5 = part(5, 10 * gb + Sizes.MIB, 5 * gb, "ext4");
        Volume l6 = part(6, 16 * gb, 4 * gb, "swap");
        List<Segment> map = Layout.segments(30 * gb, "dos", List.of(part(1, Sizes.MIB, 10 * gb - Sizes.MIB, "ntfs"),
                ext), List.of(l6, l5), null);
        assertEquals(List.of("/p1", "/p5", "/p6"), map.stream().map(s -> s.volume().id()).toList());
    }

    @Test
    void lockedVolumes() {
        assertTrue(Safety.locked(part(2, 0, 1, "ext4", "/")));
        assertTrue(Safety.locked(part(1, 0, 1, "vfat", "/boot/efi")));
        assertFalse(Safety.locked(part(1, 0, 1, "vfat", "/media/ralf/STICK")));
        Volume swap = new Volume("/s", "/dev/sda3", 3, 0, 1, "other", "swap", "", "", "", "", List.of(), -1, false,
                null, true);
        assertTrue(Safety.locked(swap));
        Volume luks = part(3, 0, 1, "crypto_LUKS").withCleartext(part(0, 0, 1, "ext4", "/home"));
        assertTrue(Safety.locked(luks));
        assertFalse(Safety.canDelete(part(0, 0, 1, "vfat")), "a file system on the whole drive is no partition");
    }

    @Test
    void namesTheFileSystemCanStore() {
        assertEquals("MY STICK", FsChoice.FAT32.label("my stick"));
        assertEquals("FOTOS2026", FsChoice.FAT32.label("Fotos.2026"));
        assertEquals("ABCDEFGHIJK", FsChoice.FAT32.label("abcdefghijklmno"));
        assertEquals("Urlaub Kroatien", FsChoice.EXFAT.label("Urlaub Kroatien 2026"));
        assertEquals("Datensicherung 2", FsChoice.EXT4.label("Datensicherung 2026"));
        assertEquals("Größe", FsChoice.NTFS.label(" Größe "));
    }

    @Test
    void suggestedChoiceAndTypes() {
        assertEquals(FsChoice.FAT32, FsChoice.suggested(16 * Sizes.GB, true));
        assertEquals(FsChoice.EXFAT, FsChoice.suggested(64 * Sizes.GB, true));
        assertEquals(FsChoice.EXT4, FsChoice.suggested(500 * Sizes.GB, false));
        assertEquals("0x0c", FsChoice.FAT32.partitionType("dos"));
        assertEquals("0fc63daf-8483-4772-8e79-3d69d8477de4", FsChoice.EXT4.partitionType("gpt"));
        assertEquals(FsChoice.NTFS, FsChoice.of("ntfs"));
        assertNull(FsChoice.of("swap"));
        assertTrue(FsChoice.ENCRYPTED.takeOwnership());
        assertFalse(FsChoice.FAT32.takeOwnership());
    }
}
