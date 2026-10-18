package eu.amadeos.system.sysinfo.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemFilesTest {
    @Test
    void osReleaseValuesLoseTheirQuotes() {
        Map<String, String> os = SystemFiles.parseOsRelease("""
                # comment
                PRETTY_NAME="Debian GNU/Linux 13 (trixie)"
                NAME='Debian GNU/Linux'
                ID=debian
                """);
        assertEquals("Debian GNU/Linux 13 (trixie)", os.get("PRETTY_NAME"));
        assertEquals("Debian GNU/Linux", os.get("NAME"));
        assertEquals("debian", os.get("ID"));
    }

    @Test
    void meminfoIsInBytes() {
        Map<String, Long> mem = SystemFiles.parseMeminfo("""
                MemTotal:        2014104 kB
                MemAvailable:    1500000 kB
                HugePages_Total:       0
                """);
        assertEquals(2014104L * 1024, mem.get("MemTotal"));
        assertEquals(1500000L * 1024, mem.get("MemAvailable"));
        assertEquals(0L, mem.get("HugePages_Total"));
    }

    @Test
    void cpuinfoCountsCoresAndThreads() {
        String two = """
                processor	: %d
                model name	: Intel(R) Core(TM)   i5-8250U CPU @ 1.60GHz
                physical id	: 0
                core id		: %d
                """;
        String text = two.formatted(0, 0) + "\n" + two.formatted(1, 1) + "\n" + two.formatted(2, 0) + "\n"
                + two.formatted(3, 1);
        SystemFiles.Cpu cpu = SystemFiles.parseCpuinfo(text);
        assertEquals("Intel(R) Core(TM) i5-8250U CPU @ 1.60GHz", cpu.model());
        assertEquals(4, cpu.threads());
        assertEquals(2, cpu.cores());
        assertEquals(1, cpu.sockets());
        // QEMU and ARM boards may leave out the core ids: then every thread counts as a core.
        assertEquals(2, SystemFiles.parseCpuinfo("processor : 0\nprocessor : 1\n").cores());
    }

    @Test
    void mountsKeepDisksAndTheRoot() {
        List<SystemFiles.Mount> mounts = SystemFiles.parseMounts("""
                proc /proc proc rw 0 0
                overlay / overlay rw 0 0
                /dev/sr0 /run/live/medium iso9660 ro 0 0
                /dev/loop0 /run/live/rootfs/filesystem.squashfs squashfs ro 0 0
                /dev/sda1 /media/user/My\\040Disk ext4 rw 0 0
                /dev/sda1 /mnt/bind ext4 rw 0 0
                tmpfs /tmp tmpfs rw 0 0
                """);
        assertEquals(List.of(
                new SystemFiles.Mount("overlay", "/", "overlay"),
                new SystemFiles.Mount("/dev/sr0", "/run/live/medium", "iso9660"),
                new SystemFiles.Mount("/dev/sda1", "/media/user/My Disk", "ext4")), mounts);
    }

    @Test
    void lspciNamesTheGraphicsAdapters() {
        List<String> adapters = SystemFiles.parseLspciGraphics("""
                00:00.0 "Host bridge" "Intel Corporation" "440FX - 82441FX PMC [Natoma]" -r02 "Red Hat, Inc." "Qemu virtual machine"
                00:02.0 "VGA compatible controller" "Device 1234" "Device 1111" -r02 "Red Hat, Inc." "Device 1100"
                01:00.0 "3D controller" "NVIDIA Corporation" "GP108M [GeForce MX150]" -ra1 "" ""
                """);
        assertEquals(List.of("Device 1234 Device 1111", "NVIDIA Corporation GP108M [GeForce MX150]"), adapters);
    }

    @Test
    void uptimeInSeconds() {
        assertEquals(Optional.of(3725L), SystemFiles.parseUptime("3725.81 7000.12\n"));
        assertEquals(Optional.empty(), SystemFiles.parseUptime(""));
    }
}
