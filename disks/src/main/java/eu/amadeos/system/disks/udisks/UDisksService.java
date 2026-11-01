package eu.amadeos.system.disks.udisks;

import eu.amadeos.system.disks.model.Drive;
import eu.amadeos.system.disks.model.FsChoice;
import eu.amadeos.system.disks.model.Health;
import eu.amadeos.system.disks.model.Volume;
import eu.amadeos.system.disks.service.DiskException;
import eu.amadeos.system.disks.service.DiskException.Reason;
import eu.amadeos.system.disks.service.DiskService;
import eu.amadeos.system.disks.service.Progress;
import eu.amadeos.system.disks.udisks.dbus.AtaAttribute;
import eu.amadeos.system.disks.udisks.dbus.Block;
import eu.amadeos.system.disks.udisks.dbus.ConfigurationItem;
import eu.amadeos.system.disks.udisks.dbus.DriveAta;
import eu.amadeos.system.disks.udisks.dbus.Encrypted;
import eu.amadeos.system.disks.udisks.dbus.Filesystem;
import eu.amadeos.system.disks.udisks.dbus.Manager;
import eu.amadeos.system.disks.udisks.dbus.NvmeController;
import eu.amadeos.system.disks.udisks.dbus.Partition;
import eu.amadeos.system.disks.udisks.dbus.PartitionTable;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.FileDescriptor;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.interfaces.ObjectManager;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.messages.MethodCall;
import org.freedesktop.dbus.types.UInt64;
import org.freedesktop.dbus.types.Variant;
import org.newsclub.net.unix.FileDescriptorCast;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link DiskService} on udisks2 ({@code org.freedesktop.UDisks2} on the system bus). udisks runs mkfs, the
 * partitioning library and fsck as root after asking polkit; for the user of the active session polkit asks for
 * the administrator's password through the amadeOS polkit agent where the rules want it (formatting, partitions,
 * images), and lets mounting and powering off sticks pass without.
 */
public final class UDisksService implements DiskService {
    private static final Logger LOGGER = Logger.getLogger(UDisksService.class.getName());
    static final String BUS_NAME = "org.freedesktop.UDisks2";
    static final String ROOT = "/org/freedesktop/UDisks2";
    static final String MANAGER = ROOT + "/Manager";
    static final long CALL_TIMEOUT_MILLIS = 12L * 3600 * 1000;

    private DBusConnection bus;

    private synchronized DBusConnection bus() throws DiskException {
        try {
            if (bus == null || !bus.isConnected()) bus = DBusConnectionBuilder.forSystemBus().build();
            return bus;
        } catch (DBusException e) {
            throw new DiskException(Reason.UNAVAILABLE, "no system bus: " + e.getMessage(), e);
        }
    }

    // ---- reading ------------------------------------------------------------------------------------------------

    /** The whole object tree, variants unwrapped (see {@link UDisksObjects}). */
    Map<String, Map<String, Map<String, Object>>> objects() throws DiskException {
        try {
            ObjectManager om = bus().getRemoteObject(BUS_NAME, ROOT, ObjectManager.class);
            Map<String, Map<String, Map<String, Object>>> out = new LinkedHashMap<>();
            for (var e : om.GetManagedObjects().entrySet()) {
                Map<String, Map<String, Object>> ifaces = new LinkedHashMap<>();
                for (var i : e.getValue().entrySet()) ifaces.put(i.getKey(), unwrap(i.getValue()));
                out.put(e.getKey().getPath(), ifaces);
            }
            return out;
        } catch (DBusException | DBusExecutionException e) {
            throw failure(e);
        }
    }

    @Override
    public List<Drive> drives() throws DiskException {
        return new UDisksObjects(objects(), UDisksService::usedAt, SystemMounts.read()).drives();
    }

    @Override
    public Progress progress(String blockId) {
        try {
            return new UDisksObjects(objects(), m -> -1, SystemMounts.NONE).progress(blockId);
        } catch (DiskException e) {
            return Progress.UNKNOWN;
        }
    }

    @Override
    public AutoCloseable watch(Runnable onChange) {
        List<AutoCloseable> handlers = new ArrayList<>();
        try {
            DBusConnection c = bus();
            handlers.add(c.addSigHandler(ObjectManager.InterfacesAdded.class, s -> onChange.run()));
            handlers.add(c.addSigHandler(ObjectManager.InterfacesRemoved.class, s -> onChange.run()));
            handlers.add(c.addSigHandler(Properties.PropertiesChanged.class, s -> {
                if (s.getPath().startsWith(ROOT) && !s.getPath().startsWith(ROOT + "/jobs")) onChange.run();
            }));
        } catch (DiskException | DBusException e) {
            LOGGER.log(Level.FINE, "Cannot follow udisks", e);
        }
        return () -> {
            for (AutoCloseable h : handlers) h.close();
        };
    }

    /** Bytes in use on the file system mounted at a path. */
    static long usedAt(String mountPoint) {
        try {
            FileStore store = Files.getFileStore(Path.of(mountPoint));
            return store.getTotalSpace() - store.getUnallocatedSpace();
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    // ---- formatting and partitions ------------------------------------------------------------------------------

    @Override
    public void formatDrive(Drive drive, FsChoice choice, String label, boolean overwrite, char[] passphrase)
            throws DiskException {
        if (drive.system()) throw new DiskException(Reason.NOT_ALLOWED, "holds the running system");
        Map<String, Variant<?>> wipe = options();
        wipe.put("tear-down", new Variant<>(true));
        if (overwrite) wipe.put("erase", new Variant<>("zero"));
        // MBR for sticks and cards up to 2 TB: TVs, car radios and cameras often know nothing else.
        String table = drive.kind() == Drive.Kind.STICK || drive.kind() == Drive.Kind.CARD
                ? drive.size() < 2_000_000_000_000L ? "dos" : "gpt" : "gpt";
        call(remote(drive.block(), Block.class), "Format", table, wipe);
        call(remote(drive.block(), PartitionTable.class), "CreatePartitionAndFormat", new UInt64(0), new UInt64(0),
                choice.partitionType(table), "", options(), choice.fsType(), formatOptions(choice, label, false,
                        passphrase));
    }

    @Override
    public void formatVolume(Volume volume, FsChoice choice, String label, boolean overwrite, char[] passphrase)
            throws DiskException {
        refuseLocked(volume);
        Map<String, Variant<?>> o = formatOptions(choice, label, overwrite, passphrase);
        o.put("tear-down", new Variant<>(true));
        if (volume.partition()) o.put("update-partition-type", new Variant<>(true));
        call(remote(volume.id(), Block.class), "Format", choice.fsType(), o);
    }

    @Override
    public void createPartition(Drive drive, long offset, long size, FsChoice choice, String label,
            char[] passphrase, boolean mountAtStart) throws DiskException {
        Object created = call(remote(drive.block(), PartitionTable.class), "CreatePartitionAndFormat",
                new UInt64(offset), new UInt64(size), choice.partitionType(drive.tableType()), "", options(),
                choice.fsType(), formatOptions(choice, label, false, passphrase));
        if (mountAtStart && !choice.encrypted() && created instanceof DBusPath path) {
            String uuid = UDisksObjects.string(objects().getOrDefault(path.getPath(), Map.of())
                    .getOrDefault(UDisksObjects.BLOCK, Map.of()), "IdUUID");
            if (!uuid.isEmpty()) addFstab(path.getPath(), uuid, choice.label(label), choice.fsType());
        }
    }

    // --8<-- [start:fstab]
    /** An fstab line for the new partition: mounted at /mnt/NAME at start, no error when it is missing. */
    private void addFstab(String block, String uuid, String name, String type) throws DiskException {
        String dir = "/mnt/" + mountName(name, uuid);
        Map<String, Variant<?>> details = new HashMap<>();
        details.put("fsname", bytes("UUID=" + uuid));
        details.put("dir", bytes(dir));
        details.put("type", bytes(type));
        details.put("opts", bytes("defaults,nofail,x-gvfs-show"));
        details.put("freq", new Variant<>(0));
        details.put("passno", new Variant<>(2));
        call(remote(block, Block.class), "AddConfigurationItem", new ConfigurationItem("fstab", details), options());
        try {
            call(remote(block, Filesystem.class), "Mount", options());
        } catch (DiskException e) {
            // Mounted at the next start then (systemd makes the folder); not worth an error now.
            LOGGER.log(Level.INFO, "New partition not mounted now: " + e.getMessage());
        }
    }
    // --8<-- [end:fstab]

    /** A folder name from the partition's name: letters, digits, - and _ only; the UUID if nothing is left. */
    static String mountName(String name, String uuid) {
        String s = name == null ? "" : name.strip().replaceAll("[^\\p{L}\\p{N}_-]+", "_");
        s = s.replaceAll("^_+|_+$", "");
        return s.isEmpty() ? uuid : s;
    }

    @Override
    public void deletePartition(Volume volume) throws DiskException {
        refuseLocked(volume);
        Map<String, Variant<?>> o = options();
        o.put("tear-down", new Variant<>(true));
        call(remote(volume.id(), Partition.class), "Delete", o);
    }

    // --8<-- [start:resize]
    @Override
    public void resize(Volume volume, long size) throws DiskException {
        boolean grow = size > volume.size();
        if (!grow) refuseLocked(volume);
        Volume content = volume.content();
        boolean hasFs = content.filesystem();
        boolean wasMounted = content.mounted();
        // ext4, FAT and NTFS shrink only unmounted; growing ext4 works while mounted (the system disk).
        if (!grow && wasMounted) unmount(content);
        if (grow) {
            call(remote(volume.id(), Partition.class), "Resize", new UInt64(size), options());
            if (hasFs && !volume.encrypted()) {
                call(remote(content.id(), Filesystem.class), "Resize", new UInt64(0), options());
            }
        } else {
            if (hasFs && !volume.encrypted()) {
                call(remote(content.id(), Filesystem.class), "Resize", new UInt64(size), options());
            }
            call(remote(volume.id(), Partition.class), "Resize", new UInt64(size), options());
        }
        if (!grow && wasMounted) mount(content);
    }
    // --8<-- [end:resize]

    @Override
    public long usedBytes(Volume volume) throws DiskException {
        Volume content = volume.content();
        if (content.used() >= 0) return content.used();
        if (!content.filesystem()) return -1;
        Map<String, Variant<?>> o = options();
        o.put("options", new Variant<>("ro"));
        String at = (String) call(remote(content.id(), Filesystem.class), "Mount", o);
        try {
            return usedAt(at);
        } finally {
            unmount(content);
        }
    }

    @Override
    public boolean[] canResize(String fsType) {
        try {
            Manager.ResizeAbility r = remote(MANAGER, Manager.class).CanResize(fsType);
            long flags = r.getFlags();
            return new boolean[]{r.isAvailable() && (flags & (4 | 16)) != 0,
                    r.isAvailable() && (flags & (2 | 8)) != 0};
        } catch (DiskException | RuntimeException e) {
            LOGGER.log(Level.FINE, "CanResize " + fsType, e);
            return new boolean[]{false, false};
        }
    }

    @Override
    public void setLabel(Volume volume, String label) throws DiskException {
        Volume c = volume.content();
        call(remote(c.id(), Filesystem.class), "SetLabel", label, options());
    }

    @Override
    public String mount(Volume volume) throws DiskException {
        return (String) call(remote(volume.content().id(), Filesystem.class), "Mount", options());
    }

    @Override
    public void unmount(Volume volume) throws DiskException {
        call(remote(volume.content().id(), Filesystem.class), "Unmount", options());
    }

    @Override
    public boolean check(Volume volume) throws DiskException {
        return fsck(volume, "Check");
    }

    @Override
    public boolean repair(Volume volume) throws DiskException {
        return fsck(volume, "Repair");
    }

    private boolean fsck(Volume volume, String method) throws DiskException {
        Volume c = volume.content();
        boolean wasMounted = c.mounted();
        if (wasMounted) unmount(c);
        try {
            return Boolean.TRUE.equals(call(remote(c.id(), Filesystem.class), method, options()));
        } finally {
            if (wasMounted) {
                try {
                    mount(c);
                } catch (DiskException e) {
                    LOGGER.log(Level.INFO, "Not mounted again after " + method, e);
                }
            }
        }
    }

    @Override
    public void unlock(Volume volume, char[] passphrase) throws DiskException {
        call(remote(volume.id(), Encrypted.class), "Unlock", new String(passphrase), options());
    }

    @Override
    public void lock(Volume volume) throws DiskException {
        if (volume.cleartext() != null && volume.cleartext().mounted()) unmount(volume.cleartext());
        call(remote(volume.id(), Encrypted.class), "Lock", options());
    }

    @Override
    public void powerOff(Drive drive) throws DiskException {
        for (Volume v : drive.volumes()) {
            if (v.content().mounted()) unmount(v);
            if (v.cleartext() != null) lock(v);
        }
        call(remote(drive.id(), eu.amadeos.system.disks.udisks.dbus.Drive.class), "PowerOff", options());
    }

    @Override
    public void eject(Drive drive) throws DiskException {
        for (Volume v : drive.volumes()) {
            if (v.content().mounted()) unmount(v);
        }
        call(remote(drive.id(), eu.amadeos.system.disks.udisks.dbus.Drive.class), "Eject", options());
    }

    // ---- health -------------------------------------------------------------------------------------------------

    @Override
    public Health health(Drive drive, boolean refresh) throws DiskException {
        Map<String, Map<String, Object>> ifaces = objects().get(drive.id());
        if (ifaces == null) return Health.UNKNOWN;
        if (ifaces.containsKey(UDisksObjects.NVME)) {
            if (refresh) call(remote(drive.id(), NvmeController.class), "SmartUpdate", options());
            Object log = call(remote(drive.id(), NvmeController.class), "SmartGetAttributes", options());
            Map<String, Object> plain = log instanceof Map<?, ?> m ? unwrap(m) : Map.of();
            return HealthReader.nvme(objects().get(drive.id()).get(UDisksObjects.NVME), plain);
        }
        if (ifaces.containsKey(UDisksObjects.ATA) && UDisksObjects.bool(ifaces.get(UDisksObjects.ATA),
                "SmartSupported")) {
            if (refresh) call(remote(drive.id(), DriveAta.class), "SmartUpdate", options());
            List<HealthReader.AtaValue> values = new ArrayList<>();
            Object list = call(remote(drive.id(), DriveAta.class), "SmartGetAttributes", options());
            if (list instanceof List<?> l) {
                for (Object o : l) {
                    if (o instanceof AtaAttribute a) {
                        values.add(new HealthReader.AtaValue(a.getId(), a.getName(), a.getValue(), a.getWorst(),
                                a.getThreshold(), a.getPretty(), a.getPrettyUnit()));
                    } else if (o instanceof Struct s) {
                        Object[] p = s.getParameters();
                        values.add(new HealthReader.AtaValue(((Number) p[0]).intValue() & 0xFF, (String) p[1],
                                ((Number) p[3]).intValue(), ((Number) p[4]).intValue(), ((Number) p[5]).intValue(),
                                ((Number) p[6]).longValue(), ((Number) p[7]).intValue()));
                    }
                }
            }
            return HealthReader.ata(objects().get(drive.id()).get(UDisksObjects.ATA), values);
        }
        return Health.UNKNOWN;
    }

    @Override
    public void startSelfTest(Drive drive, boolean longTest) throws DiskException {
        Map<String, Map<String, Object>> ifaces = objects().get(drive.id());
        if (ifaces != null && ifaces.containsKey(UDisksObjects.NVME)) {
            call(remote(drive.id(), NvmeController.class), "SmartSelftestStart", longTest ? "extended" : "short",
                    options());
        } else {
            call(remote(drive.id(), DriveAta.class), "SmartSelftestStart", longTest ? "extended" : "short",
                    options());
        }
    }

    // ---- images -------------------------------------------------------------------------------------------------

    // --8<-- [start:open]
    @Override
    public OutputStream openForRestore(Drive drive) throws DiskException {
        if (drive.system()) throw new DiskException(Reason.NOT_ALLOWED, "holds the running system");
        for (Volume v : drive.volumes()) {
            if (v.locked()) throw new DiskException(Reason.NOT_ALLOWED, "holds the running system");
            if (v.content().mounted()) unmount(v);
        }
        FileDescriptor fd = (FileDescriptor) call(remote(drive.block(), Block.class), "OpenForRestore", options());
        try {
            return new FileOutputStream(javaDescriptor(fd));
        } catch (IOException e) {
            throw new DiskException(Reason.FAILED, e.getMessage(), e);
        }
    }

    @Override
    public InputStream openForBackup(Drive drive) throws DiskException {
        FileDescriptor fd = (FileDescriptor) call(remote(drive.block(), Block.class), "OpenForBackup", options());
        try {
            return new FileInputStream(javaDescriptor(fd));
        } catch (IOException e) {
            throw new DiskException(Reason.FAILED, e.getMessage(), e);
        }
    }

    /**
     * udisks opens the device as root after the polkit check and passes the open file over the bus; only a
     * transport that receives file descriptors (junixsocket) gets it, which is why this program uses that one.
     */
    private static java.io.FileDescriptor javaDescriptor(FileDescriptor fd) throws IOException {
        return FileDescriptorCast.unsafeUsing(fd.getIntFileDescriptor()).getFileDescriptor();
    }
    // --8<-- [end:open]

    // ---- calls --------------------------------------------------------------------------------------------------

    private void refuseLocked(Volume v) throws DiskException {
        if (v.locked()) throw new DiskException(Reason.NOT_ALLOWED, "holds the running system");
    }

    private Map<String, Variant<?>> formatOptions(FsChoice choice, String label, boolean overwrite,
            char[] passphrase) {
        Map<String, Variant<?>> o = options();
        String name = choice.label(label);
        if (!name.isEmpty()) o.put("label", new Variant<>(name));
        if (overwrite) o.put("erase", new Variant<>("zero"));
        if (choice.takeOwnership()) o.put("take-ownership", new Variant<>(true));
        if (choice.encrypted() && passphrase != null) {
            o.put("encrypt.passphrase", new Variant<>(new String(passphrase)));
            o.put("encrypt.type", new Variant<>("luks2"));
        }
        // mkfs.vfat picks FAT16 for small sticks; FAT32 is what the dialog promised.
        if (choice == FsChoice.FAT32) o.put("mkfs-args", new Variant<>(new String[]{"-F", "32"}, "as"));
        return o;
    }

    /** Options of every call: polkit may ask the user (the amadeOS password dialog). */
    private static Map<String, Variant<?>> options() {
        Map<String, Variant<?>> o = new HashMap<>();
        o.put("auth.no_user_interaction", new Variant<>(false));
        return o;
    }

    private static Variant<byte[]> bytes(String s) {
        byte[] raw = s.getBytes(StandardCharsets.UTF_8);
        byte[] nul = new byte[raw.length + 1];
        System.arraycopy(raw, 0, nul, 0, raw.length);
        return new Variant<>(nul, "ay");
    }

    private <T extends DBusInterface> T remote(String path, Class<T> type) throws DiskException {
        try {
            return bus().getRemoteObject(BUS_NAME, path, type);
        } catch (DBusException e) {
            throw failure(e);
        }
    }

    // --8<-- [start:call]
    static {
        // Formatting a big drive with overwrite, or the user typing the password, takes longer than dbus-java's
        // usual 20 seconds; the calls run off the event thread, so they may wait.
        MethodCall.setDefaultTimeout(CALL_TIMEOUT_MILLIS);
    }

    /** Calls a udisks method by name on the remote object and waits for its answer. */
    private Object call(DBusInterface remote, String method, Object... args) throws DiskException {
        Method m = null;
        for (Class<?> type : remote.getClass().getInterfaces()) {
            for (Method candidate : type.getMethods()) {
                if (candidate.getName().equals(method) && candidate.getParameterCount() == args.length) m = candidate;
            }
        }
        if (m == null) throw new IllegalArgumentException("no method " + method);
        try {
            return m.invoke(remote, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof DBusExecutionException x) throw failure(x);
            throw new DiskException(Reason.FAILED, String.valueOf(cause), cause);
        } catch (IllegalAccessException e) {
            throw new DiskException(Reason.FAILED, e.getMessage(), e);
        } catch (DBusExecutionException e) {
            throw failure(e);
        }
    }
    // --8<-- [end:call]

    /** Turns a udisks error (org.freedesktop.UDisks2.Error.*) into a reason the window can explain. */
    @SuppressWarnings("removal")
    static DiskException failure(Exception e) {
        String type = e instanceof DBusExecutionException x && x.getType() != null ? x.getType() : "";
        String message = e.getMessage() == null ? type : e.getMessage();
        return new DiskException(reason(type, message), message, e);
    }

    static Reason reason(String type, String message) {
        if (type.endsWith(".NotAuthorizedDismissed") || type.endsWith(".Cancelled")) return Reason.CANCELLED;
        if (type.endsWith(".NotAuthorized") || type.endsWith(".NotAuthorizedCanObtain")) return Reason.NOT_ALLOWED;
        if (type.endsWith(".DeviceBusy") || message.contains("target is busy") || message.contains("busy")) {
            return Reason.BUSY;
        }
        if (type.endsWith(".ServiceUnknown") || type.endsWith(".NotSupported")
                || type.endsWith(".DependencyMissing")) {
            return Reason.UNAVAILABLE;
        }
        return Reason.FAILED;
    }

    // ---- variants -----------------------------------------------------------------------------------------------

    static Map<String, Object> unwrap(Map<?, ?> props) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (var e : props.entrySet()) out.put(String.valueOf(e.getKey()), plain(e.getValue()));
        return out;
    }

    private static Object plain(Object v) {
        if (v instanceof Variant<?> var) return plain(var.getValue());
        if (v instanceof DBusPath p) return p.getPath();
        if (v instanceof UInt64 u) return u.longValue();
        if (v instanceof Number || v instanceof String || v instanceof Boolean || v instanceof byte[]) return v;
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object o : l) out.add(plain(o));
            return out;
        }
        if (v instanceof Object[] a) {
            List<Object> out = new ArrayList<>();
            for (Object o : a) out.add(plain(o));
            return out;
        }
        if (v instanceof Map<?, ?> m) return unwrap(m);
        return v;
    }
}
