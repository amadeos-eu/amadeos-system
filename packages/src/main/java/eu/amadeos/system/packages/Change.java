package eu.amadeos.system.packages;

import java.util.List;

/**
 * One package that an apt run would install, upgrade or remove, from the {@code Inst} and {@code Remv}
 * lines of a simulation ({@code apt-get -s}).
 *
 * @param kind             what happens to the package
 * @param name             package name, with {@code :arch} only when apt prints one
 * @param installedVersion the version installed now, empty for a new package
 * @param newVersion       the version apt would install, empty for a removal
 * @param origins          the repositories offering the new version, empty for a removal
 * @param architecture     amd64, all, …; empty for a removal
 */
public record Change(Kind kind, String name, String installedVersion, String newVersion, List<Origin> origins,
                     String architecture) {

    public enum Kind { INSTALL, UPGRADE, REMOVE }

    public Change {
        origins = List.copyOf(origins);
    }

    /** True when one of the repositories offering the new version is a security archive. */
    public boolean security() {
        return origins.stream().anyMatch(Origin::security);
    }

    /** The label of the first origin (Debian, Debian-Security, amadeOS, …), empty when there is none. */
    public String originLabel() {
        return origins.isEmpty() ? "" : origins.getFirst().label();
    }
}
