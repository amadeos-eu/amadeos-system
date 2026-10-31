package eu.amadeos.system.users;

import java.util.List;
import java.util.Objects;

/**
 * A group of {@code /etc/group} that the Groups tab shows: a permission group of the system (audio, printers,
 * administration, …) or a group made on this computer (group ID from 1000 on).
 *
 * @param members the members listed in {@code /etc/group}; people whose primary group it is are not listed there
 */
public record UserGroup(String name, int gid, List<String> members, Kind kind) {
    /** What a group is for, which decides what the page may do with it. */
    public enum Kind {
        /** A group of the system that gives its members a permission; members can change, the group stays. */
        PERMISSION,
        /** A group made on this computer to share folders; it can be removed. */
        OWN,
        /** The primary group of one account, named like it; hidden, removed with the account. */
        PERSONAL
    }

    public UserGroup {
        Objects.requireNonNull(name);
        members = List.copyOf(members);
        Objects.requireNonNull(kind);
    }

    public boolean has(String login) {
        return members.contains(login);
    }
}
