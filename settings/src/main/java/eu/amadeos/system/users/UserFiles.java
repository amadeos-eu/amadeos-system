package eu.amadeos.system.users;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads {@code /etc/passwd} and {@code /etc/group}, which every user may read: the people's accounts and the
 * groups of the Groups tab.
 */
public final class UserFiles {
    /** The first and last user ID of people's accounts on Debian (adduser.conf FIRST_UID, LAST_UID). */
    public static final int FIRST_UID = 1000;
    public static final int LAST_UID = 59999;

    /** Shells that mean "cannot log in". */
    private static final Set<String> NO_LOGIN = Set.of("/usr/sbin/nologin", "/sbin/nologin", "/bin/false",
            "/usr/bin/false");

    private UserFiles() {
    }

    /** A line of {@code /etc/passwd}. */
    record PasswdEntry(String login, int uid, int gid, String gecos, String home, String shell) {
    }

    /** A line of {@code /etc/group}. */
    record GroupEntry(String name, int gid, List<String> members) {
    }

    static List<PasswdEntry> parsePasswd(Collection<String> lines) {
        List<PasswdEntry> entries = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] f = line.split(":", -1);
            if (f.length < 7) continue;
            try {
                entries.add(new PasswdEntry(f[0], Integer.parseInt(f[2]), Integer.parseInt(f[3]), f[4], f[5], f[6]));
            } catch (NumberFormatException e) {
                // a broken line: not an account the page can show
            }
        }
        return entries;
    }

    static List<GroupEntry> parseGroup(Collection<String> lines) {
        List<GroupEntry> entries = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] f = line.split(":", -1);
            if (f.length < 4) continue;
            try {
                List<String> members = Arrays.stream(f[3].split(",")).map(String::strip).filter(s -> !s.isEmpty())
                        .toList();
                entries.add(new GroupEntry(f[0], Integer.parseInt(f[2]), members));
            } catch (NumberFormatException e) {
                // skip
            }
        }
        return entries;
    }

    static boolean person(int uid) {
        return uid >= FIRST_UID && uid <= LAST_UID;
    }

    /** The people's accounts with their groups, sorted by name as the list shows them. */
    public static List<Account> accounts(Collection<String> passwd, Collection<String> group, Set<String> loggedIn) {
        List<GroupEntry> groups = parseGroup(group);
        Map<String, Set<String>> memberOf = new HashMap<>();
        for (GroupEntry g : groups) {
            for (String m : g.members()) memberOf.computeIfAbsent(m, k -> new LinkedHashSet<>()).add(g.name());
        }
        List<Account> accounts = new ArrayList<>();
        for (PasswdEntry p : parsePasswd(passwd)) {
            if (!person(p.uid()) || p.login().equals("nobody")) continue;
            String fullName = p.gecos().split(",", -1)[0].strip();
            accounts.add(new Account(p.login(), p.uid(), fullName, p.home(), p.shell(),
                    memberOf.getOrDefault(p.login(), Set.of()), NO_LOGIN.contains(p.shell()),
                    loggedIn.contains(p.login())));
        }
        accounts.sort(Comparator.comparing((Account a) -> a.displayName().toLowerCase(Locale.ROOT))
                .thenComparing(Account::login));
        return accounts;
    }

    /**
     * The groups the Groups tab lists: the permission groups that exist, in {@link PermissionGroups#ALL}'s order,
     * then the groups made on the computer by name. Personal groups (an account's primary group of the same name)
     * are left out.
     */
    public static List<UserGroup> groups(Collection<String> passwd, Collection<String> group) {
        Map<Integer, String> primaryOf = new HashMap<>();
        Set<String> logins = new HashSet<>();
        for (PasswdEntry p : parsePasswd(passwd)) {
            primaryOf.put(p.gid(), p.login());
            logins.add(p.login());
        }
        Map<String, GroupEntry> byName = new HashMap<>();
        for (GroupEntry g : parseGroup(group)) byName.putIfAbsent(g.name(), g);

        List<UserGroup> result = new ArrayList<>();
        for (String name : PermissionGroups.ALL) {
            GroupEntry g = byName.get(name);
            if (g != null) result.add(new UserGroup(g.name(), g.gid(), g.members(), UserGroup.Kind.PERMISSION));
        }
        byName.values().stream()
                .filter(g -> person(g.gid()) && !PermissionGroups.known(g.name()))
                .filter(g -> !(g.name().equals(primaryOf.get(g.gid())) && logins.contains(g.name())))
                .sorted(Comparator.comparing(GroupEntry::name))
                .forEach(g -> result.add(new UserGroup(g.name(), g.gid(), g.members(), UserGroup.Kind.OWN)));
        return result;
    }
}
