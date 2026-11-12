package eu.amadeos.system.software.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Notices when an upgrade replaced the Java runtime this program runs on. The desktop and its programs keep running
 * on the old runtime, which is still in memory, until they are started again; they start child processes without the
 * JDK's helper ({@code -Djdk.lang.Process.launchMechanism=FORK} in {@code /etc/amadeos/java/*.options}), so they keep
 * working, but only a new login brings everything onto the new version.
 *
 * <p>The runtime's class library ({@code lib/modules}) stands for the whole runtime: dpkg replaces it with a new file
 * on every upgrade of the Java package, and removes it when the package goes.
 */
final class JavaRuntimeWatch {

    private final Path file;
    private final Stamp started;

    /** @param javaHome the runtime's folder, {@code java.home} */
    JavaRuntimeWatch(Path javaHome) {
        this.file = javaHome.resolve("lib").resolve("modules");
        this.started = stamp(file);
    }

    /** Watches the runtime this program runs on. */
    static JavaRuntimeWatch current() {
        return new JavaRuntimeWatch(Path.of(System.getProperty("java.home")));
    }

    /** Whether the runtime's files are no longer those this program was started with. */
    boolean replaced() {
        // Without the file at the start (a runtime laid out differently) there is nothing to compare.
        return started != null && !started.equals(stamp(file));
    }

    /** The file's identity (device and inode), size and time, or null when it cannot be read. */
    private static Stamp stamp(Path file) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            return new Stamp(attributes.fileKey(), attributes.size(), attributes.lastModifiedTime().toMillis());
        } catch (IOException e) {
            return null;
        }
    }

    private record Stamp(Object key, long size, long modified) {
    }
}
