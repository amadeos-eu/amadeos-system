package eu.amadeos.system.disks.model;

import java.util.Set;

/**
 * The rules that keep the running system safe: volumes that hold it are locked (no format, delete or shrink), and a
 * drive with a locked volume cannot be formatted as a whole.
 */
public final class Safety {
    /** Mount points of the running system. */
    public static final Set<String> SYSTEM_MOUNTS = Set.of("/", "/boot", "/boot/efi", "/efi", "/usr", "/var",
            "/home", "/opt", "/srv", "/tmp", "/var/log");
    /** Members of volume groups and RAID arrays: Disks does not manage those, so it leaves them alone. */
    private static final Set<String> MEMBER_TYPES = Set.of("LVM2_member", "linux_raid_member", "zfs_member",
            "isw_raid_member", "ddf_raid_member");

    private Safety() {
    }

    /**
     * Whether a volume may not be formatted, deleted or shrunk: it holds the running system, or it is a member of a
     * volume group or RAID array.
     */
    public static boolean locked(Volume v) {
        return holdsSystem(v) || MEMBER_TYPES.contains(v.type());
    }

    /** Whether a volume (with its unlocked contents) holds the running system: system mount points, active swap. */
    public static boolean holdsSystem(Volume v) {
        if (v.swapActive()) return true;
        for (String m : v.mountPoints()) {
            if (SYSTEM_MOUNTS.contains(m)) return true;
        }
        return v.cleartext() != null && holdsSystem(v.cleartext());
    }

    /** What may be done with a volume on a drive. */
    public static boolean canFormat(Volume v) {
        return !v.locked();
    }

    public static boolean canDelete(Volume v) {
        return !v.locked() && v.partition();
    }

    /** Shrinking a locked volume is not allowed; growing is. */
    public static boolean canShrink(Volume v) {
        return !v.locked();
    }
}
