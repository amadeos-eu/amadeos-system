package eu.amadeos.system.disks;

import eu.amadeos.system.disks.model.Drive;
import eu.amadeos.system.disks.model.FsChoice;
import eu.amadeos.system.disks.model.Health;
import eu.amadeos.system.disks.model.Layout;
import eu.amadeos.system.disks.model.Safety;
import eu.amadeos.system.disks.model.Sizes;
import eu.amadeos.system.disks.model.Volume;
import eu.amadeos.system.disks.service.DiskException;
import eu.amadeos.system.disks.service.DiskService;
import eu.amadeos.system.disks.service.Progress;
import eu.amadeos.system.disks.udisks.HealthReader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A computer with a system disk (NVMe), a USB stick, a memory card and an empty disc drive, for the tests and the
 * pictures of the handbook. Steps change the drives in memory; images are kept in a byte array.
 */
public final class FakeDisks implements DiskService {
    public static final long GB = Sizes.GB;
    public final List<String> calls = new ArrayList<>();
    private List<Drive> drives = new ArrayList<>(initial());
    private byte[] stickData = new byte[0];

    public static List<Drive> initial() {
        long ssd = 1_000_204_886_016L;
        Volume efi = vol("/b/nvme0n1p1", "/dev/nvme0n1p1", 1, Sizes.MIB, 512 * Sizes.MIB, "vfat", "FAT32", "", "",
                List.of("/boot/efi"), 6 * Sizes.MB, false);
        Volume root = vol("/b/nvme0n1p2", "/dev/nvme0n1p2", 2, 513 * Sizes.MIB, 900 * GB, "ext4", "1.0", "amadeOS",
                "", List.of("/"), 312 * GB, false);
        Volume swap = vol("/b/nvme0n1p3", "/dev/nvme0n1p3", 3, 513 * Sizes.MIB + 900 * GB, 16 * GB, "swap", "", "",
                "", List.of(), -1, true);
        List<Volume> sys = List.of(efi, root, swap);
        long stick = 32_015_679_488L;
        Volume stickFs = vol("/b/sdb1", "/dev/sdb1", 1, Sizes.MIB, stick - Sizes.MIB, "vfat", "FAT32", "STICK", "",
                List.of("/media/ralf/STICK"), 4_100_000_000L, false);
        long card = 63_864_569_856L;
        Volume cardFs = vol("/b/mmcblk0p1", "/dev/mmcblk0p1", 1, 4 * Sizes.MIB, card - 4 * Sizes.MIB, "exfat",
                "1.0", "CAMERA", "", List.of(), -1, false);
        return List.of(
                drive("/d/ssd", "/b/nvme0n1", "/dev/nvme0n1", Drive.Kind.SYSTEM, "", "Samsung SSD 980 1TB", ssd,
                        "nvme", "gpt", false, false, false, true, sys),
                drive("/d/stick", "/b/sdb", "/dev/sdb", Drive.Kind.STICK, "SanDisk", "Ultra", stick, "usb", "dos",
                        true, true, false, false, List.of(stickFs)),
                drive("/d/card", "/b/mmcblk0", "/dev/mmcblk0", Drive.Kind.CARD, "", "SD card reader", card, "sdio",
                        "dos", true, false, false, false, List.of(cardFs)),
                new Drive("/d/disc", "", "/dev/sr0", Drive.Kind.DISC, "HL-DT-ST", "DVDRAM GP57EB40", "", 0, "usb",
                        "", true, false, true, false, true, -1, "", List.of(), List.of()));
    }

    static Volume vol(String id, String device, int number, long offset, long size, String type, String version,
            String label, String partName, List<String> mounts, long used, boolean swap) {
        String usage = type.equals("swap") ? "other" : "filesystem";
        Volume v = new Volume(id, device, number, offset, size, usage, type, version, label,
                Integer.toHexString(id.hashCode()).toUpperCase() + "-" + number, partName, mounts, used, false, null,
                swap);
        return v.withLocked(Safety.locked(v));
    }

    static Drive drive(String id, String block, String device, Drive.Kind kind, String vendor, String model,
            long size, String connection, String table, boolean removable, boolean powerOff, boolean eject,
            boolean smart, List<Volume> volumes) {
        return new Drive(id, block, device, kind, vendor, model, "S4EWNX0R123456", size, connection, table,
                removable, powerOff, eject, smart, true, smart ? 41 : -1, "", volumes,
                Layout.segments(size, table, volumes, List.of(), null));
    }

    public Drive drive(String device) {
        return drives.stream().filter(d -> d.device().equals(device)).findFirst().orElseThrow();
    }

    @Override
    public synchronized List<Drive> drives() {
        return List.copyOf(drives);
    }

    @Override
    public AutoCloseable watch(Runnable onChange) {
        return () -> { };
    }

    @Override
    public Progress progress(String blockId) {
        return Progress.UNKNOWN;
    }

    private synchronized void replace(Drive old, List<Volume> volumes) {
        Drive now = new Drive(old.id(), old.block(), old.device(), old.kind(), old.vendor(), old.model(),
                old.serial(), old.size(), old.connection(), old.tableType().isEmpty() ? "dos" : old.tableType(),
                old.removable(), old.canPowerOff(), old.ejectable(), old.smart(), old.healthOk(), old.temperature(),
                old.backingFile(), volumes, Layout.segments(old.size(), old.tableType().isEmpty() ? "dos"
                        : old.tableType(), volumes, List.of(), null));
        drives.replaceAll(d -> d.id().equals(old.id()) ? now : d);
    }

    private Drive owner(Volume v) {
        return drives.stream().filter(d -> d.volumes().stream().anyMatch(x -> x.id().equals(v.id())))
                .findFirst().orElseThrow();
    }

    @Override
    public void formatDrive(Drive drive, FsChoice choice, String label, boolean overwrite, char[] passphrase)
            throws DiskException {
        calls.add("formatDrive " + drive.device() + " " + choice + " " + label);
        if (drive.system()) throw new DiskException(DiskException.Reason.NOT_ALLOWED, "system");
        replace(drive, List.of(vol(drive.block() + "1", drive.device() + "1", 1, Sizes.MIB,
                drive.size() - Sizes.MIB, choice.fsType(), "", choice.label(label), "", List.of(), -1, false)));
    }

    @Override
    public void formatVolume(Volume volume, FsChoice choice, String label, boolean overwrite, char[] passphrase)
            throws DiskException {
        calls.add("formatVolume " + volume.device() + " " + choice + " " + label);
        if (volume.locked()) throw new DiskException(DiskException.Reason.NOT_ALLOWED, "system");
        Drive d = owner(volume);
        List<Volume> list = new ArrayList<>(d.volumes());
        list.replaceAll(v -> v.id().equals(volume.id()) ? vol(v.id(), v.device(), v.number(), v.offset(), v.size(),
                choice.fsType(), "", choice.label(label), "", List.of(), -1, false) : v);
        replace(d, list);
    }

    @Override
    public void createPartition(Drive drive, long offset, long size, FsChoice choice, String label,
            char[] passphrase, boolean mountAtStart) {
        calls.add("createPartition " + drive.device() + " " + offset + " " + size + " " + choice + " " + label + " "
                + mountAtStart);
        Drive d = drives.stream().filter(x -> x.id().equals(drive.id())).findFirst().orElseThrow();
        List<Volume> list = new ArrayList<>(d.volumes());
        int n = list.size() + 1;
        long s = size == 0 ? Layout.alignDown(d.size() - offset - Sizes.MIB) : size;
        list.add(vol(d.block() + "p" + n, d.device() + "p" + n, n, offset, s, choice.fsType(), "",
                choice.label(label), "", mountAtStart ? List.of("/mnt/" + label) : List.of(), -1, false));
        replace(d, list);
    }

    @Override
    public void deletePartition(Volume volume) {
        calls.add("delete " + volume.device());
        Drive d = owner(volume);
        List<Volume> list = new ArrayList<>(d.volumes());
        list.removeIf(v -> v.id().equals(volume.id()));
        replace(d, list);
    }

    @Override
    public void resize(Volume volume, long size) {
        calls.add("resize " + volume.device() + " " + size);
    }

    @Override
    public long usedBytes(Volume volume) {
        return volume.content().used() >= 0 ? volume.content().used() : 1_500_000_000L;
    }

    @Override
    public boolean[] canResize(String fsType) {
        return switch (fsType) {
            case "ext4", "ntfs" -> new boolean[]{true, true};
            case "vfat" -> new boolean[]{true, true};
            default -> new boolean[]{false, false};
        };
    }

    @Override
    public void setLabel(Volume volume, String label) {
        calls.add("label " + volume.device() + " " + label);
    }

    @Override
    public String mount(Volume volume) {
        calls.add("mount " + volume.device());
        return "/media/ralf/" + volume.label();
    }

    @Override
    public void unmount(Volume volume) {
        calls.add("unmount " + volume.device());
    }

    @Override
    public boolean check(Volume volume) {
        calls.add("check " + volume.device());
        return true;
    }

    @Override
    public boolean repair(Volume volume) {
        return true;
    }

    @Override
    public void unlock(Volume volume, char[] passphrase) {
        calls.add("unlock " + volume.device());
    }

    @Override
    public void lock(Volume volume) {
        calls.add("lock " + volume.device());
    }

    @Override
    public void powerOff(Drive drive) {
        calls.add("powerOff " + drive.device());
    }

    @Override
    public void eject(Drive drive) {
        calls.add("eject " + drive.device());
    }

    @Override
    public Health health(Drive drive, boolean refresh) {
        if (!drive.smart()) return Health.UNKNOWN;
        Map<String, Object> controller = Map.of("SmartCriticalWarning", List.of(), "SmartPowerOnHours", 4210L,
                "SmartTemperature", 314L, "SmartSelftestStatus", "success");
        Map<String, Object> log = Map.of("avail_spare", 100L, "spare_thresh", 10L, "percent_used", 3L,
                "total_data_written", 38_600_000_000_000L, "power_cycles", 1377L, "unsafe_shutdowns", 41L,
                "media_errors", 0L, "num_err_log_entries", 0L, "wctemp", 355L);
        return HealthReader.nvme(controller, log);
    }

    @Override
    public void startSelfTest(Drive drive, boolean longTest) {
        calls.add("selfTest " + drive.device() + " " + longTest);
    }

    @Override
    public OutputStream openForRestore(Drive drive) {
        calls.add("openForRestore " + drive.device());
        return new ByteArrayOutputStream() {
            @Override
            public void close() {
                stickData = toByteArray();
            }
        };
    }

    @Override
    public InputStream openForBackup(Drive drive) {
        calls.add("openForBackup " + drive.device());
        return new ByteArrayInputStream(stickData);
    }
}
