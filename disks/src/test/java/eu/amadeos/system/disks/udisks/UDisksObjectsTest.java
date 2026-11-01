package eu.amadeos.system.disks.udisks;

import eu.amadeos.system.disks.model.Drive;
import eu.amadeos.system.disks.model.Segment;
import eu.amadeos.system.disks.model.Sizes;
import eu.amadeos.system.disks.model.Volume;
import eu.amadeos.system.disks.service.Progress;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UDisksObjectsTest {
    static final String P = "/org/freedesktop/UDisks2/";
    final Map<String, Map<String, Map<String, Object>>> objects = new LinkedHashMap<>();

    static byte[] ay(String s) {
        byte[] raw = s.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[raw.length + 1];
        System.arraycopy(raw, 0, out, 0, raw.length);
        return out;
    }

    Map<String, Map<String, Object>> object(String path) {
        return objects.computeIfAbsent(path, k -> new LinkedHashMap<>());
    }

    void drive(String name, Map<String, Object> props) {
        object(P + "drives/" + name).put(UDisksObjects.DRIVE, new HashMap<>(props));
    }

    Map<String, Object> block(String name, String drive, long size) {
        Map<String, Object> b = new HashMap<>();
        b.put("Device", ay("/dev/" + name));
        b.put("Drive", drive == null ? "/" : P + "drives/" + drive);
        b.put("Size", size);
        b.put("CryptoBackingDevice", "/");
        b.put("IdUsage", "");
        b.put("IdType", "");
        object(P + "block_devices/" + name).put(UDisksObjects.BLOCK, b);
        return b;
    }

    void partition(String name, String table, int number, long offset, long size) {
        object(P + "block_devices/" + name).put(UDisksObjects.PARTITION, new HashMap<>(Map.of("Table",
                P + "block_devices/" + table, "Number", number, "Offset", offset, "Size", size, "Name", "",
                "IsContainer", false, "IsContained", false)));
    }

    void fs(String name, String type, String label, String... mounts) {
        Map<String, Object> b = object(P + "block_devices/" + name).get(UDisksObjects.BLOCK);
        b.put("IdUsage", "filesystem");
        b.put("IdType", type);
        b.put("IdLabel", label);
        b.put("IdUUID", "UUID-" + name);
        List<byte[]> points = new java.util.ArrayList<>();
        for (String m : mounts) points.add(ay(m));
        object(P + "block_devices/" + name).put(UDisksObjects.FILESYSTEM, Map.of("MountPoints", points));
    }

    void computer() {
        drive("Samsung_SSD", Map.of("Model", "Samsung SSD 980 1TB", "Size", 1000 * Sizes.GB, "ConnectionBus", ""));
        object(P + "drives/Samsung_SSD").put(UDisksObjects.NVME, Map.of("SmartCriticalWarning", List.of(),
                "SmartTemperature", 314L));
        block("nvme0n1", "Samsung_SSD", 1000 * Sizes.GB);
        object(P + "block_devices/nvme0n1").put(UDisksObjects.TABLE, Map.of("Type", "gpt"));
        block("nvme0n1p1", "Samsung_SSD", 512 * Sizes.MIB);
        partition("nvme0n1p1", "nvme0n1", 1, Sizes.MIB, 512 * Sizes.MIB);
        fs("nvme0n1p1", "vfat", "", "/boot/efi");
        block("nvme0n1p2", "Samsung_SSD", 900 * Sizes.GB);
        partition("nvme0n1p2", "nvme0n1", 2, 513 * Sizes.MIB, 900 * Sizes.GB);
        fs("nvme0n1p2", "ext4", "amadeOS", "/");

        drive("SanDisk", Map.of("Vendor", "SanDisk", "Model", "Ultra", "ConnectionBus", "usb", "Removable", true,
                "CanPowerOff", true, "Media", "thumb"));
        block("sdb", "SanDisk", 32 * Sizes.GB);
        object(P + "block_devices/sdb").put(UDisksObjects.TABLE, Map.of("Type", "dos"));
        block("sdb1", "SanDisk", 32 * Sizes.GB - Sizes.MIB);
        partition("sdb1", "sdb", 1, Sizes.MIB, 32 * Sizes.GB - Sizes.MIB);
        fs("sdb1", "vfat", "STICK", "/media/ralf/STICK");
    }

    List<Drive> drives() {
        return new UDisksObjects(objects, m -> m.equals("/") ? 312 * Sizes.GB : 4 * Sizes.GB, SystemMounts.NONE)
                .drives();
    }

    @Test
    void systemDiskComesFirstAndIsLocked() {
        computer();
        List<Drive> drives = drives();
        assertEquals(2, drives.size());
        Drive ssd = drives.get(0);
        assertEquals(Drive.Kind.SYSTEM, ssd.kind());
        assertEquals("/dev/nvme0n1", ssd.device());
        assertEquals("nvme", ssd.connection());
        assertEquals("gpt", ssd.tableType());
        assertEquals(41, ssd.temperature());
        assertTrue(ssd.smart());
        assertTrue(ssd.volumes().stream().allMatch(Volume::locked), "EFI and / are locked");
        assertEquals(312 * Sizes.GB, ssd.volumes().get(1).used());
        Segment last = ssd.segments().get(ssd.segments().size() - 1);
        assertTrue(last.free(), "free space after the partitions");
    }

    @Test
    void usbStickWithPartition() {
        computer();
        Drive stick = drives().get(1);
        assertEquals(Drive.Kind.STICK, stick.kind());
        assertEquals("SanDisk Ultra", stick.title());
        assertTrue(stick.canPowerOff());
        assertEquals(1, stick.volumes().size());
        Volume v = stick.volumes().get(0);
        assertEquals("STICK", v.name());
        assertEquals(List.of("/media/ralf/STICK"), v.mountPoints());
        assertFalse(v.locked());
        assertEquals(1, stick.segments().size(), "no free space shown below 2 MiB");
    }

    @Test
    void unpartitionedStickIsOneFreeBlock() {
        drive("Stick", Map.of("Model", "Stick", "ConnectionBus", "usb", "Size", 8 * Sizes.GB));
        block("sdc", "Stick", 8 * Sizes.GB);
        Drive d = drives().get(0);
        assertEquals(Drive.Kind.STICK, d.kind());
        assertFalse(d.hasTable());
        assertEquals(1, d.segments().size());
        assertTrue(d.segments().get(0).free());
    }

    @Test
    void largeUsbDiskIsExternal() {
        drive("Disk", Map.of("Model", "Elements", "ConnectionBus", "usb", "RotationRate", 5400));
        block("sdd", "Disk", 2000 * Sizes.GB);
        assertEquals(Drive.Kind.EXTERNAL, drives().get(0).kind());
    }

    @Test
    void cardsAndDiscs() {
        drive("Card", Map.of("Model", "SD", "ConnectionBus", "sdio"));
        block("mmcblk0", "Card", 64 * Sizes.GB);
        drive("Dvd", Map.of("Model", "DVD", "MediaCompatibility", List.of("optical_cd", "optical_dvd"),
                "Ejectable", true));
        List<Drive> d = drives();
        assertEquals(Drive.Kind.CARD, d.get(0).kind());
        assertEquals(Drive.Kind.DISC, d.get(1).kind());
        assertFalse(d.get(1).hasMedium());
    }

    @Test
    void encryptedPartitionShowsItsContents() {
        computer();
        Map<String, Object> b = object(P + "block_devices/sdb1").get(UDisksObjects.BLOCK);
        b.put("IdUsage", "crypto");
        b.put("IdType", "crypto_LUKS");
        object(P + "block_devices/sdb1").remove(UDisksObjects.FILESYSTEM);
        Map<String, Object> clear = block("dm-0", null, 31 * Sizes.GB);
        clear.put("CryptoBackingDevice", P + "block_devices/sdb1");
        fs("dm-0", "ext4", "Private", "/media/ralf/Private");
        Volume v = drives().get(1).volumes().get(0);
        assertTrue(v.encrypted());
        assertNotNull(v.cleartext());
        assertEquals("Private", v.name());
        assertEquals("/dev/dm-0", v.content().device());
        assertEquals(2, drives().size(), "the unlocked contents are no drive of their own");
    }

    @Test
    void encryptedRootLocksItsPartition() {
        computer();
        Map<String, Object> b = object(P + "block_devices/nvme0n1p2").get(UDisksObjects.BLOCK);
        b.put("IdUsage", "crypto");
        b.put("IdType", "crypto_LUKS");
        object(P + "block_devices/nvme0n1p2").remove(UDisksObjects.FILESYSTEM);
        Map<String, Object> clear = block("dm-1", null, 899 * Sizes.GB);
        clear.put("CryptoBackingDevice", P + "block_devices/nvme0n1p2");
        fs("dm-1", "ext4", "root", "/");
        Drive ssd = drives().get(0);
        assertTrue(ssd.volumes().get(1).locked());
        assertEquals(Drive.Kind.SYSTEM, ssd.kind());
    }

    @Test
    void kernelMountsLockWhatUdisksDoesNotKnow() {
        block("vda", null, 275 * Sizes.GB);
        SystemMounts kernel = SystemMounts.parse("22 1 254:0 / / rw,relatime - ext4 /dev/vda rw\n", "", s -> s,
                s -> List.of());
        Drive d = new UDisksObjects(objects, m -> -1, kernel).drives().get(0);
        assertEquals(Drive.Kind.SYSTEM, d.kind());
        assertEquals("ext4", d.volumes().get(0).type());
        assertTrue(d.volumes().get(0).locked());
        assertEquals(List.of("/"), d.volumes().get(0).mountPoints());
    }

    @Test
    void loopDevicesAreDiskImages() {
        block("loop0", null, 300 * Sizes.MB);
        object(P + "block_devices/loop0").put(UDisksObjects.LOOP, Map.of("BackingFile", ay("/home/ralf/x.img")));
        block("loop1", null, 0);
        object(P + "block_devices/loop1").put(UDisksObjects.LOOP, Map.of("BackingFile", ay("")));
        block("zram0", null, 4 * Sizes.GB);
        List<Drive> d = drives();
        assertEquals(1, d.size(), "empty loops and zram are left out");
        assertEquals(Drive.Kind.IMAGE, d.get(0).kind());
        assertEquals("x.img", d.get(0).title());
        assertEquals("/home/ralf/x.img", d.get(0).backingFile());
    }

    @Test
    void jobProgressOfABlock() {
        object(P + "jobs/7").put(UDisksObjects.JOB, Map.of("Objects", List.of(P + "block_devices/sdb"),
                "Progress", 0.25, "ProgressValid", true, "Bytes", 1000L, "Rate", 10L));
        Progress p = new UDisksObjects(objects, m -> -1, SystemMounts.NONE).progress(P + "block_devices/sdb");
        assertEquals(0.25, p.fraction(), 1e-9);
        assertEquals(250, p.done());
        assertEquals(75, p.secondsLeft());
        assertEquals(Progress.UNKNOWN, new UDisksObjects(objects, m -> -1, SystemMounts.NONE).progress("/x"));
    }

    @Test
    void lvmMembersAreLeftAlone() {
        computer();
        Map<String, Object> b = object(P + "block_devices/sdb1").get(UDisksObjects.BLOCK);
        b.put("IdUsage", "raid");
        b.put("IdType", "LVM2_member");
        object(P + "block_devices/sdb1").remove(UDisksObjects.FILESYSTEM);
        Drive stick = drives().get(1);
        assertTrue(stick.volumes().get(0).locked(), "no format or delete of a volume group member");
        assertEquals(Drive.Kind.STICK, stick.kind(), "but the stick is no system disk");
    }
}
