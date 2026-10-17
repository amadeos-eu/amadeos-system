package org.datazuul.amadeos.image;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks on the live-build configuration that are easy to break by hand.
 */
class ImageConfigurationTest {
    private static final Path HOOKS = Path.of("config/hooks");
    private static final Path LIVE_CONFIG = Path.of("config/includes.chroot/etc/live/config.conf");
    private static final Path BUILD_SCRIPT = Path.of("scripts/build-image.sh");

    /**
     * The hooks exist once, in config/hooks/normal (current Debian live-build runs
     * config/hooks/normal/*.chroot); config/hooks/*.chroot, which live-build 3.x on Ubuntu hosts runs,
     * are links to them, so the image does not depend on which live-build version built it.
     */
    @Test
    void hooksExistOnceAndAreLinkedForLiveBuild3() throws IOException {
        List<Path> normal = chrootHooks(HOOKS.resolve("normal"));
        List<Path> topLevel = chrootHooks(HOOKS);
        assertFalse(normal.isEmpty(), "expected hooks in " + HOOKS.resolve("normal"));
        assertEquals(names(normal), names(topLevel), "config/hooks must link every hook of config/hooks/normal");
        for (Path hook : normal) {
            assertTrue(Files.isRegularFile(hook, java.nio.file.LinkOption.NOFOLLOW_LINKS), hook + " must be the file");
            Path link = HOOKS.resolve(hook.getFileName());
            assertTrue(Files.isSymbolicLink(link), link + " must be a link to " + hook);
            assertEquals(Path.of("normal").resolve(hook.getFileName()), Files.readSymbolicLink(link),
                    link + " must point to the hook in normal/ by a relative path");
        }
    }

    /** The package list is the only list of packages; the hook must not install a second copy. */
    @Test
    void thePackagesHookInstallsNoPackagesOfItsOwn() throws IOException {
        String hook = Files.readString(HOOKS.resolve("normal/020-amadeos-packages.chroot"));
        assertFalse(hook.contains("apt-get install"), "packages belong in config/package-lists/amadeos.list.chroot");
        assertTrue(Files.isRegularFile(Path.of("config/package-lists/amadeos.list.chroot")));
    }

    /**
     * live-config takes the kernel command line before /etc/live/config.conf, so the settings of the live
     * image live in config.conf alone and the command line only starts live-config.
     */
    @Test
    void liveConfigSettingsHaveOneSource() throws IOException {
        String script = Files.readString(BUILD_SCRIPT);
        Matcher bootappend = Pattern.compile("(?m)^BOOTAPPEND=\"([^\"]*)\"$").matcher(script);
        assertTrue(bootappend.find(), "BOOTAPPEND must be defined in " + BUILD_SCRIPT);
        assertEquals("boot=live components", bootappend.group(1));

        String config = Files.readString(LIVE_CONFIG);
        for (String variable : List.of("LIVE_HOSTNAME", "LIVE_USERNAME", "LIVE_USER_DEFAULT_GROUPS",
                "LIVE_KEYBOARD_LAYOUTS", "LIVE_TIMEZONE")) {
            assertTrue(Pattern.compile("(?m)^" + variable + "=\"[^\"]+\"$").matcher(config).find(),
                    variable + " must be set in " + LIVE_CONFIG);
        }
        assertTrue(config.contains("lpadmin") && config.contains("systemd-journal"),
                "the live user needs lpadmin (printing) and systemd-journal (log viewer)");
    }

    @Test
    void installedScriptsAreExecutable() throws IOException {
        try (Stream<Path> scripts = Files.list(Path.of("config/includes.chroot/usr/local/bin"))) {
            for (Path script : scripts.toList()) {
                assertTrue(Files.isExecutable(script), script + " must be executable (git update-index --chmod=+x)");
            }
        }
    }

    private static List<Path> chrootHooks(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".chroot")).sorted().toList();
        }
    }

    private static List<String> names(List<Path> paths) {
        return paths.stream().map(p -> p.getFileName().toString()).toList();
    }
}
