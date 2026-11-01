package eu.amadeos.system.disks.model;

import java.util.List;

/**
 * A disk, stick, card, disc drive or attached disk image, with its partitions.
 *
 * @param id          the udisks object path of the drive (of the block device for disk images)
 * @param block       the udisks object path of the whole-drive block device, empty without medium
 * @param device      /dev/sdb
 * @param kind        what it is, for the icon and the plain name
 * @param vendor      the maker as the drive reports it, may be empty
 * @param model       the model as the drive reports it, may be empty
 * @param serial      the serial number, may be empty
 * @param size        the size in bytes, 0 without medium
 * @param connection  usb, sdio, ata, nvme, loop or empty
 * @param tableType   gpt, dos or empty (no partition table)
 * @param removable   the medium or the drive can be taken out
 * @param canPowerOff udisks can switch it off (USB drives)
 * @param ejectable   it has a tray or a slot to eject (discs)
 * @param smart       SATA SMART or NVMe health is available
 * @param healthOk    false when the drive itself reports it is failing
 * @param temperature in °C, or -1
 * @param backingFile the image file of an attached disk image, else empty
 * @param volumes     partitions in drive order (logical ones included, the extended container left out)
 * @param segments    the partition map: volumes and free space in drive order
 */
public record Drive(String id, String block, String device, Kind kind, String vendor, String model, String serial,
        long size, String connection, String tableType, boolean removable, boolean canPowerOff, boolean ejectable,
        boolean smart, boolean healthOk, int temperature, String backingFile, List<Volume> volumes,
        List<Segment> segments) {

    /** Kinds of drives, in the order of the drive list. */
    public enum Kind {
        SYSTEM, INTERNAL, EXTERNAL, STICK, CARD, DISC, IMAGE
    }

    public Drive {
        volumes = List.copyOf(volumes);
        segments = List.copyOf(segments);
    }

    /** It holds the running system: the whole drive cannot be formatted. */
    public boolean system() {
        return kind == Kind.SYSTEM;
    }

    public boolean hasMedium() {
        return size > 0 && !block.isEmpty();
    }

    /** Vendor and model as one line: "SanDisk Ultra", "Samsung SSD 980 1TB". */
    public String title() {
        String v = vendor.strip(), m = model.strip();
        if (m.isEmpty()) return v;
        if (v.isEmpty() || m.toLowerCase().startsWith(v.toLowerCase())) return m;
        return v + " " + m;
    }

    public boolean hasTable() {
        return !tableType.isEmpty();
    }

    /** Any of its file systems is mounted. */
    public boolean inUse() {
        return volumes.stream().anyMatch(v -> v.mounted() || v.content().mounted() || v.swapActive());
    }
}
