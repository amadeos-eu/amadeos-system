package org.datazuul.amadeos.sysinfo.service;

import org.datazuul.amadeos.commons.i18n.Messages;
import org.datazuul.amadeos.sysinfo.domain.Report;
import org.datazuul.amadeos.sysinfo.domain.Report.Item;
import org.datazuul.amadeos.sysinfo.domain.Report.Section;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Collects the report. Files are read below {@code root} ({@code /} on a real system, a folder in tests), the
 * network interfaces, screens and commands come from {@link Sources}. Reading takes a moment ({@code lspci}),
 * so call it off the event thread.
 */
public final class SystemProbe {
    private static final Logger LOG = LoggerFactory.getLogger(SystemProbe.class);

    /** A network interface: name, hardware address (empty if none), addresses, whether it is up. */
    public record Interface(String name, String mac, List<String> addresses, boolean up) {
    }

    /** What does not come from files. */
    public interface Sources {
        /** The output of a command, empty if it is not installed or fails. */
        Optional<String> run(List<String> command);

        List<Interface> interfaces();

        /** The screens, e.g. "1920 × 1080". */
        List<String> screens();
    }

    private final Path root;
    private final Sources sources;
    private final Messages msg;

    public SystemProbe(Path root, Sources sources, Messages msg) {
        this.root = root;
        this.sources = sources;
        this.msg = msg;
    }

    public Report report() {
        List<Section> sections = new ArrayList<>();
        sections.add(system());
        sections.add(processor());
        sections.add(memory());
        sections.add(storage());
        sections.add(graphics());
        sections.add(network());
        battery().ifPresent(sections::add);
        return new Report(sections);
    }

    private Section system() {
        List<Item> items = new ArrayList<>();
        Map<String, String> os = SystemFiles.parseOsRelease(read("etc/os-release").orElse(""));
        String name = os.getOrDefault("PRETTY_NAME", os.getOrDefault("NAME", ""));
        items.add(item("label.os", name.isEmpty() ? msg.get("value.unknown") : name));
        items.add(item("label.kernel", read("proc/sys/kernel/osrelease").map(String::trim).orElse(msg.get("value.unknown"))));
        items.add(item("label.architecture", System.getProperty("os.arch", "")));
        read("proc/sys/kernel/hostname").map(String::trim).ifPresent(h -> items.add(item("label.hostname", h)));
        read("proc/uptime").flatMap(SystemFiles::parseUptime).ifPresent(s -> items.add(item("label.uptime", uptime(s))));
        items.add(item("label.java", System.getProperty("java.version", "") + " ("
                + System.getProperty("java.vm.vendor", "") + ")"));
        return new Section(msg.get("section.system"), items);
    }

    private Section processor() {
        List<Item> items = new ArrayList<>();
        SystemFiles.Cpu cpu = SystemFiles.parseCpuinfo(read("proc/cpuinfo").orElse(""));
        items.add(item("label.model", cpu.model().isEmpty() ? msg.get("value.unknown") : cpu.model()));
        int threads = cpu.threads() > 0 ? cpu.threads() : Runtime.getRuntime().availableProcessors();
        items.add(item("label.cores", msg.format("value.cores", cpu.threads() > 0 ? cpu.cores() : threads, threads)));
        read("proc/loadavg").ifPresent(load -> {
            String[] f = load.trim().split("\\s+");
            if (f.length >= 3) items.add(item("label.load", f[0] + "  " + f[1] + "  " + f[2]));
        });
        return new Section(msg.get("section.processor"), items);
    }

    private Section memory() {
        List<Item> items = new ArrayList<>();
        Map<String, Long> mem = SystemFiles.parseMeminfo(read("proc/meminfo").orElse(""));
        long total = mem.getOrDefault("MemTotal", 0L);
        items.add(item("label.memory", total > 0 ? bytes(total) : msg.get("value.unknown")));
        if (mem.containsKey("MemAvailable")) items.add(item("label.available", bytes(mem.get("MemAvailable"))));
        long swap = mem.getOrDefault("SwapTotal", 0L);
        items.add(item("label.swap", swap > 0
                ? msg.format("value.used", bytes(swap - mem.getOrDefault("SwapFree", 0L)), bytes(swap))
                : msg.get("value.none")));
        return new Section(msg.get("section.memory"), items);
    }

    private Section storage() {
        List<Item> items = new ArrayList<>();
        for (SystemFiles.Mount mount : SystemFiles.parseMounts(read("proc/mounts").orElse(""))) {
            Path path = root.resolve(mount.mountPoint().substring(1));
            try {
                FileStore store = Files.getFileStore(path);
                long total = store.getTotalSpace();
                long free = store.getUsableSpace();
                if (total <= 0) continue;
                items.add(new Item(mount.mountPoint(), msg.format("value.disk", bytes(total - store.getUnallocatedSpace()),
                        bytes(total), bytes(free), mount.device(), mount.type())));
            } catch (IOException | SecurityException e) {
                LOG.debug("No size for {}: {}", mount.mountPoint(), e.getMessage());
            }
        }
        if (items.isEmpty()) items.add(new Item(msg.get("label.disks"), msg.get("value.unknown")));
        return new Section(msg.get("section.storage"), items);
    }

    private Section graphics() {
        List<Item> items = new ArrayList<>();
        sources.run(List.of("lspci", "-mm")).map(SystemFiles::parseLspciGraphics).orElse(List.of())
                .forEach(adapter -> items.add(item("label.adapter", adapter)));
        List<String> screens = sources.screens();
        for (int i = 0; i < screens.size(); i++) {
            items.add(new Item(screens.size() == 1 ? msg.get("label.screen") : msg.format("label.screen.n", i + 1),
                    screens.get(i)));
        }
        if (items.isEmpty()) items.add(item("label.adapter", msg.get("value.unknown")));
        return new Section(msg.get("section.graphics"), items);
    }

    private Section network() {
        List<Item> items = new ArrayList<>();
        for (Interface iface : sources.interfaces()) {
            List<String> parts = new ArrayList<>();
            parts.add(msg.get(iface.up() ? "value.up" : "value.down"));
            if (!iface.mac().isEmpty()) parts.add(iface.mac());
            parts.addAll(iface.addresses());
            items.add(new Item(iface.name(), String.join(", ", parts)));
        }
        if (items.isEmpty()) items.add(item("label.interfaces", msg.get("value.none")));
        return new Section(msg.get("section.network"), items);
    }

    private Optional<Section> battery() {
        Path supplies = root.resolve("sys/class/power_supply");
        if (!Files.isDirectory(supplies)) return Optional.empty();
        List<Item> items = new ArrayList<>();
        try (Stream<Path> list = Files.list(supplies)) {
            for (Path supply : list.sorted().toList()) {
                if (!read(supply.resolve("type")).map(String::trim).orElse("").equals("Battery")) continue;
                Optional<String> capacity = read(supply.resolve("capacity")).map(String::trim);
                String status = read(supply.resolve("status")).map(String::trim).orElse("");
                String statusKey = "battery." + status.toLowerCase(Locale.ROOT).replace(' ', '-');
                String statusText = msg.has(statusKey) ? msg.get(statusKey) : status;
                try {
                    items.add(new Item(supply.getFileName().toString(), capacity.isPresent()
                            ? msg.format("value.battery", Integer.parseInt(capacity.get()), statusText)
                            : statusText));
                } catch (NumberFormatException e) {
                    items.add(new Item(supply.getFileName().toString(), statusText));
                }
            }
        } catch (IOException e) {
            LOG.debug("Cannot list {}: {}", supplies, e.getMessage());
        }
        return items.isEmpty() ? Optional.empty() : Optional.of(new Section(msg.get("section.battery"), items));
    }

    private Item item(String labelKey, String value) {
        return new Item(msg.get(labelKey), value);
    }

    private Optional<String> read(String relative) {
        return read(root.resolve(relative));
    }

    private static Optional<String> read(Path file) {
        try {
            return Files.isReadable(file) ? Optional.of(Files.readString(file, StandardCharsets.UTF_8)) : Optional.empty();
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** A size in binary units with one decimal in the locale: "7,6 GiB". */
    String bytes(long bytes) {
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB", "PiB"};
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        NumberFormat format = NumberFormat.getNumberInstance(msg.locale());
        format.setMaximumFractionDigits(unit == 0 ? 0 : 1);
        format.setMinimumFractionDigits(unit == 0 ? 0 : 1);
        return format.format(value) + " " + units[unit];
    }

    String uptime(long seconds) {
        long minutes = seconds / 60 % 60;
        long hours = seconds / 3600 % 24;
        long days = seconds / 86400;
        return days > 0 ? msg.format("value.uptime.days", days, hours, minutes) : msg.format("value.uptime", hours, minutes);
    }
}
