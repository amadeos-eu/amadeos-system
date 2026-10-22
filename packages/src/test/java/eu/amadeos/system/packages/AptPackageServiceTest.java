package eu.amadeos.system.packages;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The service with recorded output for reading and the real helper script for changing, which runs a fake
 * apt-get: it prints recorded status lines on the status descriptor and fails when told to.
 */
class AptPackageServiceTest {
    private static final Path HELPER = Path.of("packaging/root/usr/libexec/amadeos-packages");

    @TempDir
    Path dir;
    private Path aptArgs;
    private Path log;
    private Path rebootFile;

    @BeforeEach
    void fakeApt() throws IOException {
        aptArgs = dir.resolve("apt-args");
        log = dir.resolve("amadeos-packages.log");
        rebootFile = dir.resolve("reboot-required");
        Path simulation = dir.resolve("simulate.txt");
        Files.writeString(simulation, Fixtures.read("simulate-debian.txt"));
        Path status = dir.resolve("status-fd.txt");
        Files.writeString(status, Fixtures.read("status-fd.txt"));
        Path fake = dir.resolve("apt-get");
        Files.writeString(fake, """
                #!/bin/bash
                # A simulation prints the recorded one; everything else the recorded status lines, unless
                # $FAKE_APT_FAIL says to fail like apt does.
                for a in "$@"; do [ "$a" = "-s" ] && { cat '%s'; exit 0; }; done
                echo "$*" >> '%s'
                case "${FAKE_APT_FAIL:-}" in
                    lock) echo "E: Could not get lock /var/lib/dpkg/lock-frontend. It is held by process 42 (unattended-upgr)" >&2
                          echo "E: Unable to acquire the dpkg frontend lock (/var/lib/dpkg/lock-frontend), is another process using it?" >&2
                          exit 100 ;;
                    missing) echo "E: Unable to locate package no-such-package" >&2; exit 100 ;;
                esac
                echo "Reading package lists..."
                cat '%s' >&3
                echo "Setting up distro-info-data (2026.10.12) ..."
                """.formatted(simulation, aptArgs, status));
        assertTrue(fake.toFile().setExecutable(true));
    }

    // --8<-- [start:service]
    private AptPackageService service(String fail) {
        Map<String, String> env = new java.util.HashMap<>(Map.of(
                "AMADEOS_PACKAGES_APT_GET", dir.resolve("apt-get").toString(),
                "AMADEOS_PACKAGES_LOG", log.toString(),
                "AMADEOS_PACKAGES_REBOOT_FILE", rebootFile.toString()));
        if (fail != null) env.put("FAKE_APT_FAIL", fail);
        return new AptPackageService(List.of("bash", HELPER.toString()), env, command -> {
            throw new AssertionError("no read expected: " + command);
        });
    }
    // --8<-- [end:service]

    private List<String> aptCalls() throws IOException {
        return Files.exists(aptArgs) ? Files.readAllLines(aptArgs, StandardCharsets.UTF_8) : List.of();
    }

    /** Collects what the listener hears. */
    private static final class Recorder implements ProgressListener {
        final List<Progress> progress = new ArrayList<>();
        final List<String> messages = new ArrayList<>();

        @Override
        public void progress(Progress p) {
            progress.add(p);
        }

        @Override
        public void message(String line) {
            messages.add(line);
        }
    }

    // --8<-- [start:install]
    @Test
    void installReportsProgressAndMessages() throws IOException {
        Recorder recorder = new Recorder();
        Result result = service(null).install(List.of("distro-info-data"), recorder);

        assertEquals(new Result(Result.Status.DONE, "", false), result);
        assertEquals(15, recorder.progress.size());
        assertEquals(Progress.Phase.INSTALL, recorder.progress.getLast().phase());
        assertTrue(recorder.messages.contains("Setting up distro-info-data (2026.10.12) ..."));
        String call = aptCalls().getFirst();
        assertTrue(call.contains("APT::Status-Fd=3"), call);
        assertTrue(call.endsWith("-y install distro-info-data"), call);
        assertTrue(Files.readString(log).contains("Setting up distro-info-data"), "messages are logged");
    }
    // --8<-- [end:install]

    @Test
    void upgradeAllAndRebootRequired() throws IOException {
        Files.writeString(rebootFile, "*** System restart required ***\n");
        Result result = service(null).upgrade(List.of(), false, ProgressListener.NONE);
        assertTrue(result.success());
        assertTrue(result.rebootRequired());
        assertTrue(aptCalls().getFirst().endsWith("-y --with-new-pkgs upgrade"));
    }

    @Test
    void securityOnlyUpgradesThePackagesFromTheSecurityArchive() throws IOException {
        assertTrue(service(null).upgrade(List.of(), true, ProgressListener.NONE).success());
        // linux-image-6.12.48 is new, not an upgrade; tzdata comes from stable-updates.
        assertTrue(aptCalls().getFirst().endsWith("-y install --only-upgrade libssl3t64 openssl linux-image-amd64"),
                aptCalls().getFirst());
    }

    @Test
    void securityOnlyOfNamedPackagesWithoutSecurityUpdateDoesNothing() throws IOException {
        Result result = service(null).upgrade(List.of("tzdata"), true, ProgressListener.NONE);
        assertTrue(result.success());
        assertEquals(List.of(), aptCalls(), "apt was only asked for the simulation");
    }

    @Test
    void busyLockIsReportedAsLocked() {
        Result result = service("lock").remove(List.of("gimp"), ProgressListener.NONE);
        assertEquals(Result.Status.LOCKED, result.status());
        assertEquals("another package operation is running", result.message());
    }

    @Test
    void aptErrorsBecomeTheMessage() {
        Recorder recorder = new Recorder();
        Result result = service("missing").install(List.of("no-such-package"), recorder);
        assertEquals(new Result(Result.Status.FAILED, "Unable to locate package no-such-package", false), result);
        assertTrue(recorder.messages.contains("E: Unable to locate package no-such-package"));
    }

    @Test
    void badNamesNeverReachTheHelper() throws IOException {
        Result result = service(null).install(List.of("-oDpkg::Pre-Invoke=sh"), ProgressListener.NONE);
        assertFalse(result.success());
        assertEquals(List.of(), aptCalls());
    }

    @Test
    void theHelperRefusesBadNamesItself() throws IOException {
        // The helper does not trust its caller: pkexec lets any local program start it.
        Result result = new AptPackageService(List.of("bash", HELPER.toString()),
                Map.of("AMADEOS_PACKAGES_APT_GET", dir.resolve("apt-get").toString(), "AMADEOS_PACKAGES_LOG", log.toString()),
                command -> "").upgrade(List.of(), false, ProgressListener.NONE);
        assertTrue(result.success());
        Process process = new ProcessBuilder("bash", HELPER.toString(), "install", "--allow-downgrades")
                .redirectErrorStream(true).start();
        String out;
        try (var in = process.getInputStream()) {
            out = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals("@@error usage not a package name: --allow-downgrades\n", out);
    }

    @Test
    void automaticSecurityUpdatesWriteTheAptSetting() throws Exception {
        Path conf = Files.createDirectory(dir.resolve("apt.conf.d"));
        Path dpkgQuery = dir.resolve("dpkg-query");
        // unattended-upgrades is not installed yet
        Files.writeString(dpkgQuery, "#!/bin/sh\nexit 1\n");
        assertTrue(dpkgQuery.toFile().setExecutable(true));
        AptPackageService service = new AptPackageService(List.of("bash", HELPER.toString()), Map.of(
                "AMADEOS_PACKAGES_APT_GET", dir.resolve("apt-get").toString(),
                "AMADEOS_PACKAGES_LOG", log.toString(),
                "AMADEOS_PACKAGES_APT_CONF_DIR", conf.toString(),
                "AMADEOS_PACKAGES_DPKG_QUERY", dpkgQuery.toString()), command -> "");

        assertTrue(service.setAutomaticSecurityUpdates(true, ProgressListener.NONE).success());
        assertTrue(aptCalls().getFirst().endsWith("-y install unattended-upgrades"), aptCalls().getFirst());
        String written = Files.readString(conf.resolve("50amadeos-unattended"));
        assertTrue(written.contains("APT::Periodic::Unattended-Upgrade \"1\";"), written);

        assertTrue(service.setAutomaticSecurityUpdates(false, ProgressListener.NONE).success());
        assertTrue(Files.readString(conf.resolve("50amadeos-unattended"))
                .contains("APT::Periodic::Unattended-Upgrade \"0\";"));
        assertEquals(1, aptCalls().size(), "turning it off installs nothing");
        try (var files = Files.list(conf)) {
            assertEquals(1, files.count(), "no temporary file left behind");
        }
    }

    @Test
    void listsAreThereOnceAptFetchedPackages() throws Exception {
        Path lists = Files.createDirectory(dir.resolve("lists"));
        Files.createDirectory(lists.resolve("partial"));
        Files.writeString(lists.resolve("lock"), "");
        assertFalse(AptPackageService.hasLists(lists), "a cleaned image keeps only partial/ and lock");
        Files.writeString(lists.resolve("deb.debian.org_debian_dists_trixie_main_binary-amd64_Packages.lz4"), "");
        assertTrue(AptPackageService.hasLists(lists));
        assertFalse(AptPackageService.hasLists(dir.resolve("missing")));
    }

    @Test
    void lastRefreshIsTheNewestMarker() throws Exception {
        Path lists = Files.createDirectory(dir.resolve("lists"));
        Path stamp = Files.writeString(dir.resolve("update-success-stamp"), "");
        Files.setLastModifiedTime(lists, java.nio.file.attribute.FileTime.fromMillis(1_000_000));
        Files.setLastModifiedTime(stamp, java.nio.file.attribute.FileTime.fromMillis(2_000_000));
        assertEquals(java.time.Instant.ofEpochMilli(2_000_000),
                AptPackageService.lastModified(List.of(lists, stamp, dir.resolve("missing"))).orElseThrow());
        assertTrue(AptPackageService.lastModified(List.of(dir.resolve("missing"))).isEmpty());
    }

    @Test
    void readingUsesTheRunner() throws Exception {
        List<List<String>> commands = new ArrayList<>();
        PackageService service = new AptPackageService(List.of("false"), Map.of(), command -> {
            commands.add(command);
            return switch (command.getFirst()) {
                case "apt-get" -> Fixtures.read("simulate-debian.txt");
                case "dpkg-query" -> Fixtures.read("dpkg-query.txt");
                case "apt-mark" -> Fixtures.read("showauto.txt");
                case "apt-cache" -> command.get(1).equals("show") ? Fixtures.read("show-gimp.txt")
                        : Fixtures.read("dumpavail.txt");
                default -> throw new AssertionError(command);
            };
        });
        assertEquals(4, service.upgrades().size(), "the new kernel package is not an upgrade");
        assertEquals(4, service.installed().size());
        assertEquals(4, service.available().size());
        assertTrue(service.details("gimp").orElseThrow().description().startsWith("GIMP is"));
        assertEquals(List.of("apt-get", "-s", "-o", "Debug::NoLocking=1", "--with-new-pkgs", "upgrade"),
                commands.getFirst());
    }

    // --8<-- [start:dismissed]
    @Test
    void notAuthorizedWhenPkexecIsDismissed() {
        // pkexec exits with 126 when the user closes the password dialog.
        Result result = new AptPackageService(List.of("sh", "-c", "exit 126", "pkexec"), Map.of(), command -> "")
                .refresh(ProgressListener.NONE);
        assertEquals(Result.Status.NOT_AUTHORIZED, result.status());
    }
    // --8<-- [end:dismissed]

    @Test
    void theCatalogMergesTheIndexTheInstalledPackagesAndTheHints() throws Exception {
        Path lists = dir.resolve("lists");
        Files.createDirectories(lists);
        Files.writeString(lists.resolve("deb.debian.org_debian_dists_trixie_main_dep11_Components-amd64.yml.gz"), "");
        Path menu = dir.resolve("gimp.desktop");
        Files.writeString(menu, "[Desktop Entry]\nType=Application\nName=GIMP\nExec=gimp\n");
        Path hidden = dir.resolve("helper.desktop");
        Files.writeString(hidden, "[Desktop Entry]\nType=Application\nNoDisplay=true\n");
        PackageService service = new AptPackageService(List.of("false"), Map.of(), command -> {
            String first = command.getFirst();
            if (first.equals("apt-cache")) return Fixtures.read("dumpavail.txt");
            if (first.equals("apt-mark")) return Fixtures.read("showauto.txt");
            if (first.endsWith("apt-helper")) return "---\nType: console-application\nPackage: ripgrep\n";
            if (first.equals("dpkg-query") && command.get(1).equals("-W")) return Fixtures.read("dpkg-query.txt");
            return switch (command.get(2)) {
                case "/usr/share/applications/*.desktop" -> "gimp: " + menu + "\nadduser: " + hidden + "\n";
                case "/usr/bin/*" -> "openssl: /usr/bin/openssl\n";
                default -> throw new PackageService.PackageException("dpkg-query: no path found matching pattern");
            };
        }, lists);
        List<CatalogEntry> catalog = service.catalog();
        assertEquals(List.of("adduser", "gimp", "held-pkg", "libc6", "libpcre2-8-0", "openssl", "ripgrep", "unrar"),
                catalog.stream().map(CatalogEntry::name).toList());
        Map<String, CatalogEntry> byName = new java.util.HashMap<>();
        catalog.forEach(e -> byName.put(e.name(), e));
        assertEquals(Category.APPLICATION, byName.get("gimp").category());
        assertEquals(Category.COMMAND_LINE, byName.get("ripgrep").category());
        assertEquals(Category.COMMAND_LINE, byName.get("openssl").category());
        assertEquals(Category.COMMAND_LINE, byName.get("adduser").category(), "a hidden menu entry is no application");
        assertEquals(Category.LIBRARY, byName.get("libc6").category());
        assertTrue(byName.get("libc6").automatic());
        assertTrue(byName.get("libc6").installed());
        assertEquals("", byName.get("libc6").availableVersion(), "no repository in the fixture has it");
        assertFalse(byName.get("gimp").installed());
        assertEquals("graphics", byName.get("gimp").section());
    }

    @Test
    void menuEntriesSayWhatTheyShow() throws Exception {
        Path app = dir.resolve("app.desktop");
        Files.writeString(app, "[Desktop Entry]\nType=Application\nExec=app\n[Desktop Action new]\nNoDisplay=true\n");
        Path terminal = dir.resolve("top.desktop");
        Files.writeString(terminal, "[Desktop Entry]\nType=Application\nTerminal=true\n");
        assertEquals(Category.APPLICATION, AptPackageService.menuEntry(app));
        assertEquals(Category.COMMAND_LINE, AptPackageService.menuEntry(terminal));
        assertEquals(Category.OTHER, AptPackageService.menuEntry(dir.resolve("missing.desktop")));
    }
}
