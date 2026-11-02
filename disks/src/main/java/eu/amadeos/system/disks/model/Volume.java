package eu.amadeos.system.disks.model;

import java.util.List;

/**
 * A partition, or a file system on a whole drive without partition table.
 *
 * @param id         the udisks object path of the block device
 * @param device     /dev/sdb1
 * @param number     the partition number, 0 for a file system on the whole drive
 * @param offset     where it starts on the drive, in bytes
 * @param size       its size in bytes
 * @param usage      udisks' IdUsage: filesystem, crypto, other (swap), raid, or empty
 * @param type       udisks' IdType: vfat, exfat, ext4, ntfs, crypto_LUKS, swap, LVM2_member, or empty
 * @param version    udisks' IdVersion, for FAT32 vs FAT16
 * @param label      the file system's name
 * @param uuid       the file system's UUID
 * @param partitionName the GPT partition name (empty on MBR)
 * @param mountPoints where it is mounted
 * @param used       bytes in use when mounted, else -1
 * @param locked     part of the running system: no format, delete or shrink
 * @param cleartext  the unlocked contents of an encrypted volume, or null (locked or not encrypted)
 * @param swapActive an active swap area
 */
public record Volume(String id, String device, int number, long offset, long size, String usage, String type,
        String version, String label, String uuid, String partitionName, List<String> mountPoints, long used,
        boolean locked, Volume cleartext, boolean swapActive) {

    public Volume {
        mountPoints = List.copyOf(mountPoints);
    }

    public boolean mounted() {
        return !mountPoints.isEmpty();
    }

    public boolean filesystem() {
        return "filesystem".equals(usage);
    }

    public boolean encrypted() {
        return "crypto_LUKS".equals(type);
    }

    public boolean partition() {
        return number > 0;
    }

    /** The name to show: the file system's label, else the GPT name, else empty. */
    public String name() {
        if (cleartext != null && !cleartext.label().isEmpty()) return cleartext.label();
        if (!label.isEmpty()) return label;
        return partitionName;
    }

    /** The volume whose file system the user sees: the unlocked contents for an encrypted one. */
    public Volume content() {
        return cleartext != null ? cleartext : this;
    }

    public Volume withUsed(long bytes) {
        return new Volume(id, device, number, offset, size, usage, type, version, label, uuid, partitionName,
                mountPoints, bytes, locked, cleartext, swapActive);
    }

    public Volume withLocked(boolean value) {
        return new Volume(id, device, number, offset, size, usage, type, version, label, uuid, partitionName,
                mountPoints, used, value, cleartext, swapActive);
    }

    public Volume withCleartext(Volume value) {
        return new Volume(id, device, number, offset, size, usage, type, version, label, uuid, partitionName,
                mountPoints, used, locked, value, swapActive);
    }
}
