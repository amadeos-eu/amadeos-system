package eu.amadeos.system.packages;

/**
 * Where a package version comes from, as apt names it in a simulation: {@code Debian:13.1/stable},
 * {@code Debian-Security:13/stable-security}, or {@code amadeOS:trixie} for a repository without a version.
 *
 * @param label   the repository's label (Debian, Debian-Security, amadeOS, …)
 * @param version the release version, empty when the repository gives none
 * @param archive suite or code name (stable, stable-security, trixie-updates, …)
 */
public record Origin(String label, String version, String archive) {

    /** Security archives are named {@code <suite>-security} in Debian and its derivatives. */
    public boolean security() {
        return archive.endsWith("-security");
    }

    /** Parses one origin of a simulation line; the label may contain spaces and colons. */
    static Origin parse(String text) {
        String s = text.strip();
        int colon = s.lastIndexOf(':');
        String label = colon < 0 ? "" : s.substring(0, colon);
        String rest = s.substring(colon + 1);
        int slash = rest.indexOf('/');
        return slash < 0 ? new Origin(label, "", rest)
                : new Origin(label, rest.substring(0, slash), rest.substring(slash + 1));
    }

    @Override
    public String toString() {
        return label + ":" + (version.isEmpty() ? "" : version + "/") + archive;
    }
}
