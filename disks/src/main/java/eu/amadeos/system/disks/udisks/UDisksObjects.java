package eu.amadeos.system.disks.udisks;

import eu.amadeos.system.disks.model.Drive;
import eu.amadeos.system.disks.model.Layout;
import eu.amadeos.system.disks.model.Safety;
import eu.amadeos.system.disks.model.Segment;
import eu.amadeos.system.disks.model.Volume;
import eu.amadeos.system.disks.service.Progress;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * Reads udisks' object tree ({@code GetManagedObjects}, with the D-Bus variants already unwrapped) into drives with
 * their partitions. Pure, so it can be tested without udisks: object path → interface → property → value, where
 * object paths are strings, {@code ay} is {@code byte[]} and {@code aay} a list of {@code byte[]}.
 */
public final class UDisksObjects {
    public static final String PREFIX = "org.freedesktop.UDisks2.";
    static final String BLOCK = PREFIX + "Block";
    static final String DRIVE = PREFIX + "Drive";
    static final String ATA = PREFIX + "Drive.Ata";
    static final String NVME = PREFIX + "NVMe.Controller";
    static final String PARTITION = PREFIX + "Partition";
    static final String TABLE = PREFIX + "PartitionTable";
    static final String FILESYSTEM = PREFIX + "Filesystem";
    static final String SWAP = PREFIX + "Swapspace";
    static final String LOOP = PREFIX + "Loop";
    static final String JOB = PREFIX + "Job";
    static final String NO_OBJECT = "/";

    private final Map<String, Map<String, Map<String, Object>>> objects;
    private final ToLongFunction<String> usedAt;
    private final SystemMounts kernel;

    /**
     * @param usedAt bytes in use on the file system mounted at a path, -1 if not known
     * @param kernel what the kernel has mounted, to lock system devices udisks does not know
     */
    public UDisksObjects(Map<String, Map<String, Map<String, Object>>> objects, ToLongFunction<String> usedAt,
            SystemMounts kernel) {
        this.objects = objects;
        this.usedAt = usedAt;
        this.kernel = kernel;
    }

    /** All drives with a medium or a slot, in list order: system disk first, discs and images last. */
    public List<Drive> drives() {
        List<Drive> out = new ArrayList<>();
        for (var e : sorted(objects.entrySet())) {
            Map<String, Object> d = e.getValue().get(DRIVE);
            if (d == null) continue;
            String whole = wholeBlockOf(e.getKey());
            out.add(drive(e.getKey(), e.getValue(), whole));
        }
        for (var e : sorted(objects.entrySet())) {
            Map<String, Object> b = e.getValue().get(BLOCK);
            if (b == null || !path(b, "Drive").equals(NO_OBJECT)) continue;
            if (e.getValue().containsKey(PARTITION) || !path(b, "CryptoBackingDevice").equals(NO_OBJECT)) continue;
            if (bool(b, "HintIgnore") || number(b, "Size") <= 0) continue;
            String dev = bytes(b, "PreferredDevice").isEmpty() ? bytes(b, "Device") : bytes(b, "PreferredDevice");
            if (dev.startsWith("/dev/zram") || dev.startsWith("/dev/ram") || dev.startsWith("/dev/dm-")
                    || dev.startsWith("/dev/md")) {
                continue;
            }
            out.add(drive(e.getKey(), e.getValue(), e.getKey()));
        }
        out.sort(Comparator.comparing(Drive::kind).thenComparing(Drive::device));
        return out;
    }

    /** The running job on a block device (or on its drive), or {@link Progress#UNKNOWN}. */
    public Progress progress(String blockId) {
        for (var e : objects.entrySet()) {
            Map<String, Object> j = e.getValue().get(JOB);
            if (j == null || !paths(j, "Objects").contains(blockId)) continue;
            double fraction = bool(j, "ProgressValid") ? ((Number) j.getOrDefault("Progress", 0d)).doubleValue() : -1;
            long total = number(j, "Bytes");
            long rate = number(j, "Rate");
            return new Progress(fraction, fraction >= 0 && total > 0 ? (long) (fraction * total) : -1,
                    total > 0 ? total : -1, rate > 0 ? rate : -1);
        }
        return Progress.UNKNOWN;
    }

    private Drive drive(String id, Map<String, Map<String, Object>> ifaces, String whole) {
        Map<String, Object> d = ifaces.getOrDefault(DRIVE, Map.of());
        Map<String, Object> wb = whole.isEmpty() ? Map.of() : objects.get(whole).get(BLOCK);
        String device = whole.isEmpty() ? "" : device(wb);
        long size = whole.isEmpty() ? 0 : number(wb, "Size");
        String table = whole.isEmpty() ? "" : string(objects.get(whole).getOrDefault(TABLE, Map.of()), "Type");

        List<Volume> top = new ArrayList<>();
        List<Volume> logical = new ArrayList<>();
        List<Volume> all = new ArrayList<>();
        Volume wholeVolume = null;
        if (!whole.isEmpty()) {
            for (var e : sorted(objects.entrySet())) {
                Map<String, Object> p = e.getValue().get(PARTITION);
                if (p == null || !path(p, "Table").equals(whole)) continue;
                Volume v = volume(e.getKey(), e.getValue(), (int) number(p, "Number"), number(p, "Offset"),
                        number(p, "Size"), string(p, "Name"), bool(p, "IsContainer"));
                if (bool(p, "IsContained")) logical.add(v);
                else top.add(v);
                if (!bool(p, "IsContainer")) all.add(v);
            }
            if (table.isEmpty() && (!string(wb, "IdUsage").isEmpty() || !kernel.mountPoints(device).isEmpty())) {
                wholeVolume = volume(whole, objects.get(whole), 0, 0, size, "", false);
                all.add(wholeVolume);
            }
        }
        all.sort(Comparator.comparingLong(Volume::offset));
        List<Segment> segments = Layout.segments(size, table, top, logical, wholeVolume);

        Map<String, Object> ata = ifaces.get(ATA);
        Map<String, Object> nvme = ifaces.get(NVME);
        boolean smart = ata != null && bool(ata, "SmartSupported") || nvme != null;
        boolean ok = true;
        int temperature = -1;
        if (ata != null && bool(ata, "SmartEnabled")) {
            ok = !bool(ata, "SmartFailing");
            double k = ata.get("SmartTemperature") instanceof Number n ? n.doubleValue() : 0;
            if (k > 0) temperature = (int) Math.round(k - 273.15);
        }
        if (nvme != null) {
            ok = strings(nvme, "SmartCriticalWarning").isEmpty();
            long k = number(nvme, "SmartTemperature");
            if (k > 0) temperature = (int) (k - 273);
        }

        String connection = connection(d, device, ifaces.containsKey(LOOP));
        boolean locked = kernel.system(device) || all.stream().anyMatch(v -> Safety.holdsSystem(v)
                || kernel.system(v.device()) || v.cleartext() != null && kernel.system(v.cleartext().device()));
        Drive.Kind kind = kind(d, device, connection, size, locked);
        String backing = ifaces.containsKey(LOOP) ? bytes(ifaces.get(LOOP), "BackingFile") : "";
        String vendor = string(d, "Vendor"), model = string(d, "Model");
        if (kind == Drive.Kind.IMAGE && model.isEmpty()) model = backing.substring(backing.lastIndexOf('/') + 1);
        return new Drive(id, whole, device, kind, vendor, model, string(d, "Serial"), size, connection, table,
                bool(d, "Removable") || bool(d, "MediaRemovable"), bool(d, "CanPowerOff"), bool(d, "Ejectable"),
                smart, ok, temperature, backing, all, segments);
    }

    private Volume volume(String path, Map<String, Map<String, Object>> ifaces, int number, long offset, long size,
            String partitionName, boolean container) {
        Map<String, Object> b = ifaces.get(BLOCK);
        List<String> mounts = merged(mountPoints(ifaces), kernel.mountPoints(device(b)));
        long used = mounts.isEmpty() ? -1 : usedAt.applyAsLong(mounts.get(0));
        boolean swap = ifaces.containsKey(SWAP) && bool(ifaces.get(SWAP), "Active");
        String type = container ? "extended" : string(b, "IdType");
        String usage = string(b, "IdUsage");
        if (type.isEmpty() && !kernel.fsType(device(b)).isEmpty()) {
            type = kernel.fsType(device(b));
            usage = "filesystem";
        }
        Volume v = new Volume(path, device(b), number, offset, size, usage, type,
                string(b, "IdVersion"), string(b, "IdLabel"), string(b, "IdUUID"), partitionName, mounts, used,
                false, null, swap);
        for (var e : objects.entrySet()) {
            Map<String, Object> c = e.getValue().get(BLOCK);
            if (c != null && path(c, "CryptoBackingDevice").equals(path)) {
                Map<String, Map<String, Object>> ci = e.getValue();
                List<String> cm = merged(mountPoints(ci), kernel.mountPoints(device(c)));
                Volume clear = new Volume(e.getKey(), device(c), 0, 0, number(c, "Size"), string(c, "IdUsage"),
                        string(c, "IdType"), string(c, "IdVersion"), string(c, "IdLabel"), string(c, "IdUUID"), "",
                        cm, cm.isEmpty() ? -1 : usedAt.applyAsLong(cm.get(0)), false, null,
                        ci.containsKey(SWAP) && bool(ci.get(SWAP), "Active"));
                v = v.withCleartext(clear.withLocked(Safety.locked(clear) || kernel.system(clear.device())));
            }
        }
        return v.withLocked(Safety.locked(v) || kernel.system(v.device()));
    }

    private static List<String> merged(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a);
        for (String m : b) {
            if (!out.contains(m)) out.add(m);
        }
        return out;
    }

    private String wholeBlockOf(String drivePath) {
        for (var e : sorted(objects.entrySet())) {
            Map<String, Object> b = e.getValue().get(BLOCK);
            if (b == null || !path(b, "Drive").equals(drivePath)) continue;
            if (e.getValue().containsKey(PARTITION) || !path(b, "CryptoBackingDevice").equals(NO_OBJECT)) continue;
            return e.getKey();
        }
        return "";
    }

    private static Drive.Kind kind(Map<String, Object> d, String device, String connection, long size,
            boolean locked) {
        if (connection.equals("loop")) return Drive.Kind.IMAGE;
        if (strings(d, "MediaCompatibility").stream().anyMatch(m -> m.startsWith("optical"))
                || bool(d, "Optical")) {
            return Drive.Kind.DISC;
        }
        String media = string(d, "Media");
        if (device.startsWith("/dev/mmcblk") || connection.equals("sdio") || media.startsWith("flash_sd")
                || media.startsWith("flash_ms") || media.startsWith("flash_cf") || media.startsWith("flash_mmc")
                || strings(d, "MediaCompatibility").stream().anyMatch(m -> m.startsWith("flash_sd"))) {
            return locked ? Drive.Kind.SYSTEM : Drive.Kind.CARD;
        }
        if (locked) return Drive.Kind.SYSTEM;
        if (connection.equals("usb") || connection.equals("ieee1394")) {
            if (media.equals("thumb")) return Drive.Kind.STICK;
            if (number(d, "RotationRate") > 0) return Drive.Kind.EXTERNAL;
            return size > 0 && size <= 256L * 1000 * 1000 * 1000 ? Drive.Kind.STICK : Drive.Kind.EXTERNAL;
        }
        return Drive.Kind.INTERNAL;
    }

    private static String connection(Map<String, Object> d, String device, boolean loop) {
        if (loop) return "loop";
        String bus = string(d, "ConnectionBus");
        if (!bus.isEmpty()) return bus;
        if (device.startsWith("/dev/nvme")) return "nvme";
        if (device.startsWith("/dev/mmcblk")) return "sdio";
        if (device.startsWith("/dev/vd")) return "virtio";
        if (device.startsWith("/dev/sd")) return "ata";
        return "";
    }

    private static List<String> mountPoints(Map<String, Map<String, Object>> ifaces) {
        Map<String, Object> fs = ifaces.get(FILESYSTEM);
        if (fs == null) return List.of();
        List<String> out = new ArrayList<>();
        Object v = fs.get("MountPoints");
        if (v instanceof Collection<?> c) {
            for (Object o : c) out.add(text(o));
        } else if (v instanceof byte[][] arr) {
            for (byte[] a : arr) out.add(text(a));
        }
        return out;
    }

    private static String device(Map<String, Object> b) {
        String p = bytes(b, "PreferredDevice");
        return p.isEmpty() ? bytes(b, "Device") : p;
    }

    private static <V> List<Map.Entry<String, V>> sorted(Collection<Map.Entry<String, V>> entries) {
        List<Map.Entry<String, V>> list = new ArrayList<>(entries);
        list.sort(Map.Entry.comparingByKey());
        return list;
    }

    // ---- values -------------------------------------------------------------------------------------------------

    static String string(Map<String, Object> props, String name) {
        Object v = props.get(name);
        return v == null ? "" : v.toString();
    }

    static String path(Map<String, Object> props, String name) {
        Object v = props.get(name);
        return v == null ? NO_OBJECT : v.toString();
    }

    static List<String> paths(Map<String, Object> props, String name) {
        return strings(props, name);
    }

    static boolean bool(Map<String, Object> props, String name) {
        return Boolean.TRUE.equals(props.get(name));
    }

    static long number(Map<String, Object> props, String name) {
        return props.get(name) instanceof Number n ? n.longValue() : 0;
    }

    static List<String> strings(Map<String, Object> props, String name) {
        Object v = props.get(name);
        List<String> out = new ArrayList<>();
        if (v instanceof Collection<?> c) {
            for (Object o : c) out.add(String.valueOf(o));
        } else if (v instanceof Object[] arr) {
            for (Object o : arr) out.add(String.valueOf(o));
        }
        return out;
    }

    /** A byte string ({@code ay}) without its closing NUL. */
    static String bytes(Map<String, Object> props, String name) {
        return text(props.get(name));
    }

    private static String text(Object v) {
        if (v instanceof byte[] a) {
            int n = a.length;
            while (n > 0 && a[n - 1] == 0) n--;
            return new String(a, 0, n, StandardCharsets.UTF_8);
        }
        if (v instanceof List<?> l) {
            byte[] a = new byte[l.size()];
            for (int i = 0; i < a.length; i++) a[i] = ((Number) l.get(i)).byteValue();
            return text(a);
        }
        return v == null ? "" : v.toString();
    }
}
