package eu.amadeos.system.packages;

/**
 * A package installed on this system ({@code dpkg-query}).
 *
 * @param name          package name ({@code :arch} for packages of a foreign architecture)
 * @param version       installed version
 * @param section       Debian section
 * @param summary       one-line description
 * @param installedSize size on disk in KiB
 * @param automatic     installed as a dependency of another package, not by the user
 *                      ({@code apt-mark showauto}); such packages are not offered for removal
 */
public record InstalledPackage(String name, String version, String section, String summary, long installedSize,
                               boolean automatic) {
}
