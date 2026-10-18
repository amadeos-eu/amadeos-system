package eu.amadeos.sysinfo.service;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.sysinfo.bootstrap.SysinfoModule;
import eu.amadeos.sysinfo.domain.Report;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemProbeTest {
    private static final Messages EN = Messages.load(SysinfoModule.class, SysinfoModule.MESSAGES, Locale.US);

    @TempDir
    Path root;

    private final SystemProbe.Sources sources = new SystemProbe.Sources() {
        @Override
        public Optional<String> run(List<String> command) {
            return command.getFirst().equals("lspci")
                    ? Optional.of("00:02.0 \"VGA compatible controller\" \"Red Hat, Inc.\" \"Virtio GPU\" -r01 \"\" \"\"\n")
                    : Optional.empty();
        }

        @Override
        public List<SystemProbe.Interface> interfaces() {
            return List.of(new SystemProbe.Interface("ens3", "52:54:00:12:34:56", List.of("10.0.2.15/24"), true));
        }

        @Override
        public List<String> screens() {
            return List.of("1024 × 768");
        }
    };

    private void write(String relative, String text) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    private static Map<String, String> items(Report report, String section) {
        return report.sections().stream().filter(s -> s.title().equals(section)).findFirst().orElseThrow()
                .items().stream().collect(Collectors.toMap(Report.Item::label, Report.Item::value));
    }

    @Test
    void readsTheSystemFromItsFiles() throws IOException {
        write("etc/os-release", "PRETTY_NAME=\"amadeOS (Debian 13)\"\n");
        write("proc/sys/kernel/osrelease", "6.12.48-amd64\n");
        write("proc/sys/kernel/hostname", "amadeos\n");
        write("proc/uptime", "93780.5 1000.0\n");
        write("proc/cpuinfo", "processor : 0\nmodel name : QEMU Virtual CPU\nprocessor : 1\nmodel name : QEMU Virtual CPU\n");
        write("proc/loadavg", "0.15 0.10 0.05 1/123 4567\n");
        write("proc/meminfo", "MemTotal: 2097152 kB\nMemAvailable: 1048576 kB\nSwapTotal: 0 kB\n");
        write("proc/mounts", "proc /proc proc rw 0 0\n");
        write("sys/class/power_supply/AC/type", "Mains\n");
        write("sys/class/power_supply/BAT0/type", "Battery\n");
        write("sys/class/power_supply/BAT0/capacity", "87\n");
        write("sys/class/power_supply/BAT0/status", "Discharging\n");

        Report report = new SystemProbe(root, sources, EN).report();

        Map<String, String> system = items(report, "System");
        assertEquals("amadeOS (Debian 13)", system.get("Operating system"));
        assertEquals("6.12.48-amd64", system.get("Kernel"));
        assertEquals("amadeos", system.get("Computer name"));
        assertEquals("1 d 2 h 3 min", system.get("Running for"));

        Map<String, String> cpu = items(report, "Processor");
        assertEquals("QEMU Virtual CPU", cpu.get("Model"));
        assertEquals("2 cores, 2 threads", cpu.get("Cores"));
        assertEquals("0.15  0.10  0.05", cpu.get("Load (1, 5, 15 min)"));

        Map<String, String> memory = items(report, "Memory");
        assertEquals("2.0 GiB", memory.get("Memory"));
        assertEquals("1.0 GiB", memory.get("Available"));
        assertEquals("none", memory.get("Swap space"));

        assertEquals("Red Hat, Inc. Virtio GPU", items(report, "Graphics").get("Graphics adapter"));
        assertEquals("1024 × 768", items(report, "Graphics").get("Screen"));
        assertEquals("connected, 52:54:00:12:34:56, 10.0.2.15/24", items(report, "Network").get("ens3"));
        assertEquals("87 %, discharging", items(report, "Battery").get("BAT0"));
    }

    @Test
    void anEmptySystemStillGivesAPage() {
        Report report = new SystemProbe(root, sources, EN).report();
        assertEquals("unknown", items(report, "System").get("Operating system"));
        assertEquals("unknown", items(report, "Storage").get("Disks"));
        assertFalse(report.sections().stream().anyMatch(s -> s.title().equals("Battery")));
    }

    @Test
    void sizesAndTimesInTheLanguage() {
        SystemProbe en = new SystemProbe(root, sources, EN);
        assertEquals("512 B", en.bytes(512));
        assertEquals("1.5 KiB", en.bytes(1536));
        assertEquals("0 h 5 min", en.uptime(300));
        SystemProbe de = new SystemProbe(root, sources,
                Messages.load(SysinfoModule.class, SysinfoModule.MESSAGES, Locale.GERMANY));
        assertEquals("7,5 GiB", de.bytes(7L * 1024 * 1024 * 1024 + 512L * 1024 * 1024));
        assertTrue(de.uptime(90000).startsWith("1 T"));
    }
}
