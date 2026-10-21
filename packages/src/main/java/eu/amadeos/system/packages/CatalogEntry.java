package eu.amadeos.system.packages;

/**
 * One package of the catalog: what the repositories offer, merged with what is installed.
 *
 * @param name             package name
 * @param summary          the one-line description
 * @param section          Debian section without the component
 * @param category         the tab it belongs to
 * @param installedVersion the installed version, empty when it is not installed
 * @param availableVersion the version the repositories offer, empty when none does (a locally installed or
 *                         obsolete package)
 * @param automatic        installed as a dependency of another package, not by the user
 */
public record CatalogEntry(String name, String summary, String section, Category category,
                           String installedVersion, String availableVersion, boolean automatic) {

    public boolean installed() {
        return !installedVersion.isEmpty();
    }

    /** Installed, and the repositories offer another version. */
    public boolean upgradable() {
        return installed() && !availableVersion.isEmpty() && !availableVersion.equals(installedVersion);
    }
}
