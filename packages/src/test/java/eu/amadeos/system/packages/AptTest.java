package eu.amadeos.system.packages;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AptTest {

    @Test
    void debianSimulationWithSecurityUpdatesAndANewKernel() {
        List<Change> changes = Apt.parseSimulation(Fixtures.read("simulate-debian.txt"));
        assertEquals(5, changes.size(), "Inst lines only, not Conf");

        Change openssl = changes.get(1);
        assertEquals(new Change(Change.Kind.UPGRADE, "openssl", "3.5.1-1", "3.5.1-1+deb13u1",
                List.of(new Origin("Debian-Security", "13", "stable-security")), "amd64"), openssl);
        assertTrue(openssl.security());
        assertEquals("Debian-Security", openssl.originLabel());

        Change tzdata = changes.get(2);
        assertEquals(List.of(new Origin("Debian", "13.1", "stable"), new Origin("Debian", "13.1", "stable-updates")),
                tzdata.origins());
        assertFalse(tzdata.security());
        assertEquals("all", tzdata.architecture());

        Change kernel = changes.get(3);
        assertEquals(Change.Kind.INSTALL, kernel.kind());
        assertEquals("", kernel.installedVersion());
        assertTrue(kernel.security(), "one of several origins is a security archive");
    }

    @Test
    void ubuntuSimulationRecordedOnARealSystem() {
        List<Change> changes = Apt.parseSimulation(Fixtures.read("simulate-ubuntu.txt"));
        assertEquals(8, changes.size());
        assertTrue(changes.stream().allMatch(c -> c.kind() == Change.Kind.UPGRADE));
        // A label with a space and no release version
        Change docker = changes.stream().filter(c -> c.name().equals("docker-ce")).findFirst().orElseThrow();
        assertEquals(new Origin("Docker CE", "", "noble"), docker.origins().getFirst());
        assertEquals("5:29.9.1-1~ubuntu.24.04~noble", docker.installedVersion());
        assertEquals("5:29.9.2-1~ubuntu.24.04~noble", docker.newVersion());
        // "Inst ... []" (packages apt had to consider broken) parses like any other line
        Change lzmaDev = changes.stream().filter(c -> c.name().equals("liblzma-dev")).findFirst().orElseThrow();
        assertTrue(lzmaDev.security());
        assertEquals(4, changes.stream().filter(Change::security).count());
    }

    @Test
    void removals() {
        List<Change> changes = Apt.parseSimulation(Fixtures.read("simulate-remove.txt"));
        assertEquals(List.of("gimp", "gimp-data", "amade-pad"), changes.stream().map(Change::name).toList());
        assertEquals(new Change(Change.Kind.REMOVE, "amade-pad", "0.3.0", "", List.of(), ""), changes.get(2));
    }

    @Test
    void packagesIndex() {
        List<PackageInfo> packages = Apt.parsePackages(Fixtures.read("dumpavail.txt"));
        assertEquals(List.of("gimp", "ripgrep", "libpcre2-8-0", "unrar"), packages.stream().map(PackageInfo::name).toList());

        PackageInfo gimp = packages.getFirst();
        assertEquals("GNU Image Manipulation Program", gimp.summary());
        assertEquals("", gimp.description(), "the index has no long description");
        assertEquals("graphics", gimp.section());
        assertEquals(21456, gimp.installedSize());
        assertEquals(4912340, gimp.downloadSize());
        assertEquals("https://www.gimp.org/", gimp.homepage());
        assertEquals("gimp-data (>= 3.0.4)", gimp.depends().getFirst());
        assertTrue(gimp.tags().contains("interface::graphical"));
        assertTrue(gimp.tags().contains("works-with::image:raster"), "tags continue on the next lines");

        PackageInfo ripgrep = packages.get(1);
        assertEquals(List.of("interface::commandline", "role::program", "scope::utility"), ripgrep.tags());
        assertEquals("utils", packages.get(3).section(), "the component is not part of the section");
        assertEquals("1:7.1.8-1", packages.get(3).version());
    }

    @Test
    void longDescriptionFromShow() {
        PackageInfo gimp = Apt.parsePackages(Fixtures.read("show-gimp.txt")).getFirst();
        assertEquals("GNU Image Manipulation Program", gimp.summary());
        assertEquals("""
                GIMP is an advanced picture editor. You can use it to edit, enhance, and
                retouch photos and scans, create drawings, and make your own images.

                It has a large collection of professional-level editing tools and filters,
                similar to the ones you might find in Photoshop.""", gimp.description());
    }

    @Test
    void installedPackagesWithAutomaticOnes() {
        List<InstalledPackage> installed = Apt.parseInstalled(Fixtures.read("dpkg-query.txt"),
                Apt.parseAutomatic(Fixtures.read("showauto.txt")));
        assertEquals(List.of("adduser", "libc6:amd64", "openssl", "held-pkg"),
                installed.stream().map(InstalledPackage::name).toList(), "removed packages are left out");
        assertEquals(new InstalledPackage("adduser", "3.152", "admin", "add and remove users and groups", 429, false),
                installed.getFirst());
        assertTrue(installed.get(1).automatic(), "showauto names packages without the architecture");
        assertTrue(installed.get(3).automatic());
    }

    @Test
    void search() {
        assertEquals(List.of("apt-cache", "search", "c\\+\\+", "editor"), Apt.search(" c++  editor "));
        List<PackageService.SearchHit> hits = Apt.parseSearch(Fixtures.read("search.txt"));
        assertEquals(2, hits.size());
        assertEquals(new PackageService.SearchHit("rga",
                "ripgrep, but also search in PDFs, E-Books, Office documents, zip, tar.gz, etc."), hits.get(1));
    }

    @Test
    void namesThatCouldBeOptionsAreRefused() {
        assertTrue(Apt.validName("libstdc++6"));
        assertTrue(Apt.validName("libc6:i386"));
        assertFalse(Apt.validName("-oAPT::Foo=1"));
        assertFalse(Apt.validName("../x.deb"));
        assertFalse(Apt.validName("Gimp"));
        assertThrows(IllegalArgumentException.class, () -> Apt.simulateInstall(List.of("--purge")));
        assertEquals(List.of("apt-get", "-s", "-o", "Debug::NoLocking=1", "remove", "gimp"),
                Apt.simulateRemove(List.of("gimp")));
    }

    @Test
    void automaticSecurityUpdatesSetting() {
        assertTrue(Apt.parseUnattendedUpgrade("VALUE='1'\n"));
        assertTrue(Apt.parseUnattendedUpgrade("VALUE='always'\n"));
        assertFalse(Apt.parseUnattendedUpgrade("VALUE='0'\n"));
        assertFalse(Apt.parseUnattendedUpgrade(""), "not set at all");
    }

    @Test
    void originsWithAndWithoutVersion() {
        assertEquals(new Origin("amadeOS", "", "trixie"), Origin.parse("amadeOS:trixie"));
        assertEquals("Debian-Security:13/stable-security", Origin.parse(" Debian-Security:13/stable-security").toString());
        assertTrue(Origin.parse("Debian:13/trixie-security").security());
        assertFalse(Set.of(Origin.parse("Debian:13.1/stable")).iterator().next().security());
    }
}
