package eu.amadeos.system.disks.model;

/**
 * What a drive or partition is formatted for, in the words the Format dialog uses. Each choice knows the
 * file system udisks makes, the partition types for MBR and GPT tables and how long a name may be.
 */
public enum FsChoice {
    /** Every computer, TV, car radio and camera reads it; files up to 4 GB. */
    FAT32("vfat", "0x0c", "ebd0a0a2-b9e5-4433-87c0-68b6b72699c7", 11, true),
    /** Every current computer; big files. */
    EXFAT("exfat", "0x07", "ebd0a0a2-b9e5-4433-87c0-68b6b72699c7", 15, false),
    /** amadeOS and Linux; keeps owners and rights. */
    EXT4("ext4", "0x83", "0fc63daf-8483-4772-8e79-3d69d8477de4", 16, false),
    /** Windows. */
    NTFS("ntfs", "0x07", "ebd0a0a2-b9e5-4433-87c0-68b6b72699c7", 32, false),
    /** LUKS2 with ext4 inside; asks for the password when plugged in. */
    ENCRYPTED("ext4", "0x83", "0fc63daf-8483-4772-8e79-3d69d8477de4", 16, false);

    private final String fsType;
    private final String mbrType;
    private final String gptType;
    private final int maxLabel;
    private final boolean upperCaseLabel;

    FsChoice(String fsType, String mbrType, String gptType, int maxLabel, boolean upperCaseLabel) {
        this.fsType = fsType;
        this.mbrType = mbrType;
        this.gptType = gptType;
        this.maxLabel = maxLabel;
        this.upperCaseLabel = upperCaseLabel;
    }

    /** The type for udisks' Format: vfat, exfat, ext4, ntfs. */
    public String fsType() {
        return fsType;
    }

    public boolean encrypted() {
        return this == ENCRYPTED;
    }

    /** The partition type for a table of udisks' type {@code dos} or {@code gpt}. */
    public String partitionType(String tableType) {
        return "gpt".equals(tableType) ? gptType : mbrType;
    }

    /** Whether the new file system belongs to the user (and not to root), so the user may write to it. */
    public boolean takeOwnership() {
        return this == EXT4 || this == ENCRYPTED;
    }

    /** The name as the file system can store it: FAT only upper case, all of them only so many characters. */
    public String label(String name) {
        String s = name == null ? "" : name.strip();
        if (upperCaseLabel) s = s.toUpperCase(java.util.Locale.ROOT);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (upperCaseLabel && "*?.,;:/\\|+=<>[]\"".indexOf(c) >= 0) continue;
            b.append(c);
        }
        // ext4 and FAT count bytes, not characters; one byte per character is the safe guess for the limit.
        while (b.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxLabel) {
            b.setLength(b.length() - 1);
        }
        return b.toString();
    }

    public int maxLabel() {
        return maxLabel;
    }

    /** The choice the Format dialog suggests: FAT32 up to 32 GB (it works everywhere), exFAT above. */
    public static FsChoice suggested(long size, boolean removable) {
        if (!removable) return EXT4;
        return size <= 32L * Sizes.GB ? FAT32 : EXFAT;
    }

    /** The choice for an existing file system type (vfat, exfat, ext4, ntfs, crypto_LUKS), or null. */
    public static FsChoice of(String idType) {
        return switch (idType == null ? "" : idType) {
            case "vfat" -> FAT32;
            case "exfat" -> EXFAT;
            case "ext4", "ext3", "ext2" -> EXT4;
            case "ntfs" -> NTFS;
            case "crypto_LUKS" -> ENCRYPTED;
            default -> null;
        };
    }
}
