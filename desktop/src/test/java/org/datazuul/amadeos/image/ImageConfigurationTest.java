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
     * The hooks exist once, in config/hooks/normal, where current Debian live-build runs them. live-build
     * 3.x on Ubuntu hosts runs config/hooks/*.chroot instead, so the build script links the hooks there
     * in its copy of the configuration; the repository keeps no second copy.
     */
    @Test
    void hooksExistOnceAndTheBuildScriptLinksThemForLiveBuild3() throws IOException {
        List<Path> normal = chrootHooks(HOOKS.resolve("normal"));
        assertFalse(normal.isEmpty(), "expected hooks in " + HOOKS.resolve("normal"));
        assertEquals(List.of(), chrootHooks(HOOKS), "hooks belong in config/hooks/normal only");
        for (Path hook : normal) {
            assertTrue(Files.isExecutable(hook), hook + " must be executable (git update-index --chmod=+x)");
        }
        String script = Files.readString(BUILD_SCRIPT);
        assertTrue(script.contains("ln -s \"normal/$(basename \"$hook\")\" \"$BUILD_ROOT/config/hooks/"),
                BUILD_SCRIPT + " must link config/hooks/normal/*.chroot into config/hooks for live-build 3.0");
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
