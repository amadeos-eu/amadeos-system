package eu.amadeos.system.packages;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The installed and available software of the system, and the operations that change it. The one place in
 * amadeOS that talks to apt: the front panel's update cell, the update window and the app store use it, no
 * other module parses apt's output.
 * <p>
 * Reading needs no privileges. Changing goes through the helper {@code /usr/libexec/amadeos-packages}, which
 * runs as root through pkexec and asks for the administrator's password (refreshing the lists does not).
 * All calls block, some for minutes, so callers run them off the event dispatch thread.
 */
public interface PackageService {

    /** The upgrades a plain upgrade would install now, from the package lists last fetched. */
    List<Change> upgrades() throws PackageException;

    /** What installing the packages would change, dependencies included. */
    List<Change> simulateInstall(List<String> names) throws PackageException;

    /** What removing the packages would change, packages that depend on them included. */
    List<Change> simulateRemove(List<String> names) throws PackageException;

    /** The packages installed on this system. */
    List<InstalledPackage> installed() throws PackageException;

    /**
     * Every package the configured repositories offer, one entry per package and architecture, without long
     * descriptions (see {@link #details}). Tens of thousands of entries on Debian; takes a few seconds.
     */
    List<PackageInfo> available() throws PackageException;

    /**
     * Every package on offer or installed, sorted by name, each in its {@link Category}: the Software
     * program's catalog. Reads all package indexes; takes a few seconds.
     */
    List<CatalogEntry> catalog() throws PackageException;

    /** One package with its long description, or empty when no repository has it. */
    Optional<PackageInfo> details(String name) throws PackageException;

    /** Packages whose name or description contains all the words, as {@code apt-cache search} finds them. */
    List<SearchHit> search(String words) throws PackageException;

    /** Fetches the package lists again ({@code apt-get update}). */
    Result refresh(ProgressListener listener);

    /**
     * Upgrades the given packages, or all upgradable ones when the list is empty.
     *
     * @param securityOnly only upgrades that come from a security archive
     */
    Result upgrade(List<String> names, boolean securityOnly, ProgressListener listener);

    Result install(List<String> names, ProgressListener listener);

    /** Removes the packages; their configuration files stay, nothing is removed automatically. */
    Result remove(List<String> names, ProgressListener listener);

    /**
     * Calls {@code onChange} (on a background thread) after the installed packages or the package lists have
     * changed, once the change has settled, until the returned handle is closed. Covers changes made by this
     * service, by apt in a terminal, by unattended-upgrades and by Debian's daily list refresh.
     */
    AutoCloseable watch(Runnable onChange);

    /** When the package lists were last fetched (by the daily timer, Check now or apt in a terminal). */
    Optional<Instant> lastRefresh();

    /** Whether security updates are installed automatically (unattended-upgrades through Debian's timers). */
    boolean automaticSecurityUpdates() throws PackageException;

    /** Turns automatic security updates on (installing unattended-upgrades if needed) or off. */
    Result setAutomaticSecurityUpdates(boolean on, ProgressListener listener);

    /** A line of {@code apt-cache search}. */
    record SearchHit(String name, String summary) {
    }

    /** Reading the package database failed (apt or dpkg missing, broken lists, …). */
    final class PackageException extends Exception {
        public PackageException(String message) {
            super(message);
        }
    }
}
