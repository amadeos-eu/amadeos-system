package eu.amadeos.system.disks.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.disks.model.Drive;
import eu.amadeos.system.disks.model.Sizes;
import eu.amadeos.system.disks.model.Volume;
import eu.amadeos.system.disks.service.DiskService;

import java.util.Locale;

/**
 * What the windows of Disks share: texts, the disk service and the user's name (for the mount folder hint).
 *
 * @param service the drives: udisks, or a fake one for tests and the pictures of the handbook
 */
public record Context(Messages texts, DiskService service, String user) {

    public Locale locale() {
        return texts.locale();
    }

    public String size(long bytes) {
        return Sizes.bytes(bytes, locale());
    }

    /** "USB stick", "System disk", ... */
    public String kind(Drive d) {
        return texts.get("kind." + d.kind().name().toLowerCase(Locale.ROOT));
    }

    /** "USB stick · SanDisk Ultra · 32 GB" */
    public String driveLine(Drive d) {
        StringBuilder b = new StringBuilder(kind(d));
        if (!d.title().isEmpty()) b.append(" · ").append(d.title());
        if (d.size() > 0) b.append(" · ").append(size(d.size()));
        return b.toString();
    }

    /** The file system in words: "FAT32 (all systems)", "ext4 (Linux)", "Encrypted (LUKS)", "Swap". */
    public String fsName(Volume v) {
        String type = v.type();
        if (v.encrypted()) {
            return v.cleartext() == null ? texts.get("fs.locked") : texts.format("fs.encrypted", fsName(v.cleartext()));
        }
        if (type.equals("vfat")) {
            String version = v.version().startsWith("FAT") ? v.version() : "FAT";
            return texts.format("fs.vfat", version);
        }
        String key = "fs." + type.replace('_', '-').toLowerCase(Locale.ROOT);
        if (texts.has(key)) return texts.get(key);
        if (type.isEmpty()) return v.usage().isEmpty() ? texts.get("fs.unknown") : v.usage();
        return type;
    }

    /** The short name shown in the partition map: "FAT32", "ext4", "Swap". */
    public String fsShort(Volume v) {
        if (v.encrypted()) return v.cleartext() == null ? "LUKS" : "LUKS · " + fsShort(v.cleartext());
        if (v.type().equals("vfat")) return v.version().startsWith("FAT") ? v.version() : "FAT";
        if (v.type().equals("swap")) return texts.get("fs.swap");
        if (v.type().equals("ntfs")) return "NTFS";
        if (v.type().equals("exfat")) return "exFAT";
        return v.type().isEmpty() ? "" : v.type();
    }

    /** The name of a volume, or "Partition 3" when it has none. */
    public String volumeName(Volume v) {
        if (!v.name().isEmpty()) return v.name();
        return v.partition() ? texts.format("volume.unnamed", Integer.toString(v.number())) : texts.get("volume.whole");
    }
}
