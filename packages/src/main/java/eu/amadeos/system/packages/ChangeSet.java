package eu.amadeos.system.packages;

import java.util.ArrayList;
import java.util.List;

/**
 * Packages to install, upgrade and remove together in one apt run: what the user marked in Software, across
 * its tabs. One run means one password prompt and one dependency resolution for all of it.
 *
 * @param install packages to install
 * @param upgrade installed packages to bring to the new version; an automatic one stays automatic
 * @param remove  packages to remove (configuration files stay)
 */
public record ChangeSet(List<String> install, List<String> upgrade, List<String> remove) {

    public ChangeSet {
        install = List.copyOf(install);
        upgrade = List.copyOf(upgrade);
        remove = List.copyOf(remove);
    }

    public boolean isEmpty() {
        return install.isEmpty() && upgrade.isEmpty() && remove.isEmpty();
    }

    public int size() {
        return install.size() + upgrade.size() + remove.size();
    }

    /** All names, in the order install, upgrade, remove. */
    public List<String> names() {
        List<String> names = new ArrayList<>(install);
        names.addAll(upgrade);
        names.addAll(remove);
        return names;
    }

    /** The arguments of {@code amadeos-packages apply}: each group after its option, empty groups left out. */
    public List<String> helperArguments() {
        List<String> args = new ArrayList<>(List.of("apply"));
        group(args, "--install", install);
        group(args, "--upgrade", upgrade);
        group(args, "--remove", remove);
        return args;
    }

    /** apt-get install's names for the same change: removals with a trailing minus. */
    public List<String> aptArguments() {
        List<String> args = new ArrayList<>(install);
        args.addAll(upgrade);
        remove.forEach(name -> args.add(name + "-"));
        return args;
    }

    private static void group(List<String> args, String option, List<String> names) {
        if (names.isEmpty()) return;
        args.add(option);
        args.addAll(names);
    }
}
