package eu.amadeos.system.packages;

import java.util.List;

/**
 * A package as the repositories describe it ({@code apt-cache dumpavail}, {@code apt-cache show}).
 *
 * @param name          package name
 * @param version       the version on offer (the candidate)
 * @param architecture  amd64, all, …
 * @param section       Debian section without the component (utils, libs, x11, …)
 * @param summary       the one-line description
 * @param description   the long description with paragraphs separated by an empty line; empty when the
 *                      index has none (Debian keeps long descriptions in the Translation files, which
 *                      {@code apt-cache show} reads but {@code dumpavail} does not)
 * @param installedSize size on disk in KiB
 * @param downloadSize  size of the .deb in bytes
 * @param homepage      upstream home page, empty when unknown
 * @param depends       the Depends field split at commas, alternatives kept as written ({@code a | b})
 * @param tags          debtags ({@code role::program}, {@code interface::commandline}, …) when the index has them
 */
public record PackageInfo(String name, String version, String architecture, String section, String summary,
                          String description, long installedSize, long downloadSize, String homepage,
                          List<String> depends, List<String> tags) {

    public PackageInfo {
        depends = List.copyOf(depends);
        tags = List.copyOf(tags);
    }
}
