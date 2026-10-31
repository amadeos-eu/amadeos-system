package eu.amadeos.system.settings;

import eu.amadeos.system.users.Account;
import eu.amadeos.system.users.PermissionGroups;
import eu.amadeos.system.users.UserFiles;
import eu.amadeos.system.users.UserGroup;
import eu.amadeos.system.users.UserService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A family computer for the Users page: Ralf (administrator, logged in, the one running the Settings), Anna
 * (administrator), Tom and Lisa (standard users) and an old guest account that is disabled, with the usual
 * permission groups and a group "family" for a shared folder. Keeps /etc/passwd and /etc/group as lines and
 * changes them as the helper would, so the page reads them back through {@link UserFiles}.
 */
final class FakeUsers implements UserService {
    final List<String> passwd = new ArrayList<>(List.of(
            "root:x:0:0:root:/root:/bin/bash",
            "nobody:x:65534:65534:nobody:/nonexistent:/usr/sbin/nologin",
            "ralf:x:1000:1000:Ralf Eichinger,,,:/home/ralf:/bin/bash",
            "anna:x:1001:1001:Anna Eichinger:/home/anna:/bin/bash",
            "tom:x:1002:1002:Tom Eichinger:/home/tom:/bin/bash",
            "lisa:x:1003:1003:Lisa Eichinger:/home/lisa:/bin/bash",
            "gast:x:1004:1004:Gast:/home/gast:/usr/sbin/nologin"));
    final Map<String, List<String>> group = new LinkedHashMap<>();
    final Map<String, Integer> gids = new LinkedHashMap<>();
    final List<String> calls = new ArrayList<>();
    UserException failNext;

    FakeUsers() {
        String all = "ralf,anna,tom,lisa,gast";
        put("sudo", 27, "ralf,anna");
        put("lpadmin", 120, "ralf,anna");
        put("netdev", 106, all);
        put("bluetooth", 112, all);
        put("audio", 29, all);
        put("video", 44, all);
        put("plugdev", 46, all);
        put("scanner", 118, "ralf,anna,tom,lisa");
        put("cdrom", 24, "ralf,anna,tom");
        put("dialout", 20, "ralf,tom");
        put("systemd-journal", 999, "ralf");
        put("kvm", 993, "ralf");
        for (String p : List.of("ralf:1000", "anna:1001", "tom:1002", "lisa:1003", "gast:1004")) {
            String[] f = p.split(":");
            put(f[0], Integer.parseInt(f[1]), "");
        }
        put("family", 1010, "ralf,anna,tom,lisa");
    }

    private void put(String name, int gid, String members) {
        group.put(name, new ArrayList<>(members.isEmpty() ? List.of() : Arrays.asList(members.split(","))));
        gids.put(name, gid);
    }

    private List<String> groupLines() {
        List<String> lines = new ArrayList<>();
        group.forEach((name, m) -> lines.add(name + ":x:" + gids.get(name) + ":" + String.join(",", m)));
        return lines;
    }

    private void maybeFail(String call) throws UserException {
        calls.add(call);
        if (failNext != null) {
            UserException e = failNext;
            failNext = null;
            throw e;
        }
    }

    @Override
    public List<Account> accounts() {
        return UserFiles.accounts(passwd, groupLines(), Set.of("ralf"));
    }

    @Override
    public List<UserGroup> groups() {
        return UserFiles.groups(passwd, groupLines());
    }

    @Override
    public String self() {
        return "ralf";
    }

    @Override
    public void add(String login, String fullName, boolean admin, char[] password) throws UserException {
        maybeFail("add " + login + " " + (admin ? "admin" : "standard") + " " + fullName + " " + new String(password));
        int uid = 1000 + passwd.size();
        passwd.add(login + ":x:" + uid + ":" + uid + ":" + fullName + ":/home/" + login + ":/bin/bash");
        put(login, uid, "");
        for (String g : PermissionGroups.NEW_ACCOUNT) if (group.containsKey(g)) group.get(g).add(login);
        if (admin) group.get("sudo").add(login);
    }

    @Override
    public void remove(String login, boolean deleteHome) throws UserException {
        maybeFail("remove " + login + " " + deleteHome);
        passwd.removeIf(l -> l.startsWith(login + ":"));
        group.remove(login);
        group.values().forEach(m -> m.remove(login));
    }

    @Override
    public void setFullName(String login, String fullName) throws UserException {
        maybeFail("name " + login + " " + fullName);
        passwd.replaceAll(l -> {
            if (!l.startsWith(login + ":")) return l;
            String[] f = l.split(":", -1);
            f[4] = fullName;
            return String.join(":", f);
        });
    }

    @Override
    public void setAdmin(String login, boolean admin) throws UserException {
        maybeFail("admin " + login + " " + admin);
        if (admin) group.get("sudo").add(login);
        else group.get("sudo").remove(login);
    }

    @Override
    public void setPassword(String login, char[] password) throws UserException {
        maybeFail("password " + login + " " + new String(password));
    }

    @Override
    public void changeOwnPassword(char[] current, char[] password) throws UserException {
        maybeFail("own-password " + new String(current) + " " + new String(password));
    }

    @Override
    public void setDisabled(String login, boolean disabled) throws UserException {
        maybeFail((disabled ? "disable " : "enable ") + login);
        passwd.replaceAll(l -> {
            if (!l.startsWith(login + ":")) return l;
            String[] f = l.split(":", -1);
            f[6] = disabled ? "/usr/sbin/nologin" : "/bin/bash";
            return String.join(":", f);
        });
    }

    @Override
    public void join(String login, String g) throws UserException {
        maybeFail("join " + login + " " + g);
        group.get(g).add(login);
    }

    @Override
    public void leave(String login, String g) throws UserException {
        maybeFail("leave " + login + " " + g);
        group.get(g).remove(login);
    }

    @Override
    public void addGroup(String g) throws UserException {
        maybeFail("group-add " + g);
        put(g, 1000 + group.size(), "");
    }

    @Override
    public void removeGroup(String g) throws UserException {
        maybeFail("group-remove " + g);
        group.remove(g);
    }
}
