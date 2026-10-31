package eu.amadeos.system.users;

import java.util.List;

/**
 * The groups of a Debian system that give their members a permission, in the order the Groups tab lists them.
 * The page names each with a sentence ({@code users.group.<name>} in the texts) and shows only those that
 * exist; the helper lets members join and leave only these and the groups made on the computer.
 */
public final class PermissionGroups {
    /** The groups the Groups tab offers, administration first. */
    public static final List<String> ALL = List.of(Account.ADMIN_GROUP, "lpadmin", "netdev", "bluetooth", "audio",
            "video", "plugdev", "scanner", "cdrom", "dialout", "systemd-journal", "kvm", "libvirt", "docker");

    /**
     * The groups a new account is put in, as the installer does for the first user (without sudo, which only
     * administrators get).
     */
    public static final List<String> NEW_ACCOUNT = List.of("audio", "cdrom", "dip", "floppy", "video", "plugdev",
            "netdev", "powerdev", "scanner", "bluetooth", "lpadmin");

    private PermissionGroups() {
    }

    public static boolean known(String group) {
        return ALL.contains(group);
    }
}
