package org.datazuul.amadeos.sysinfo.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Parsers for the Linux files the information comes from; pure functions, so the tests need no real system. */
public final class SystemFiles {
    /** File systems on disks and media; others (proc, tmpfs, squashfs of the live image, ...) are left out. */
    static final Set<String> DISK_TYPES = Set.of("ext2", "ext3", "ext4", "btrfs", "xfs", "vfat", "exfat", "ntfs",
            "ntfs3", "fuseblk", "f2fs", "jfs", "reiserfs", "iso9660", "udf");

    private SystemFiles() {
    }

    /** {@code /etc/os-release}: KEY=value lines, values optionally quoted. */
    public static Map<String, String> parseOsRelease(String text) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : text.split("\n")) {
            int eq = line.indexOf('=');
            if (line.isBlank() || line.startsWith("#") || eq < 1) continue;
            String value = line.substring(eq + 1).trim();
            if (value.length() >= 2 && (value.startsWith("\"") || value.startsWith("'"))
                    && value.charAt(value.length() - 1) == value.charAt(0)) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(line.substring(0, eq).trim(), value.replace("\\\"", "\""));
        }
        return values;
    }

    /** {@code /proc/meminfo}: the sizes in bytes, by name (MemTotal, MemAvailable, SwapTotal, ...). */
    public static Map<String, Long> parseMeminfo(String text) {
        Map<String, Long> values = new LinkedHashMap<>();
        for (String line : text.split("\n")) {
            int colon = line.indexOf(':');
            if (colon < 1) continue;
            String[] parts = line.substring(colon + 1).trim().split("\\s+");
            try {
                long value = Long.parseLong(parts[0]);
                if (parts.length > 1 && parts[1].equalsIgnoreCase("kB")) value *= 1024;
                values.put(line.substring(0, colon).trim(), value);
            } catch (NumberFormatException ignored) {
                // not a number: skip the line
            }
        }
        return values;
    }

    /** What {@code /proc/cpuinfo} says about the processor. */
    public record Cpu(String model, int threads, int cores, int sockets) {
    }

    public static Cpu parseCpuinfo(String text) {
        String model = null;
        int threads = 0;
        Set<String> cores = new LinkedHashSet<>();
        Set<String> sockets = new LinkedHashSet<>();
        String physical = "0";
        for (String line : text.split("\n")) {
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            switch (key) {
                case "processor" -> threads++;
                // x86 "model name"; ARM has "Model" (the board) or only "CPU part", so take what there is.
                case "model name", "Model", "cpu model", "Processor" -> {
                    if (model == null && !value.isEmpty()) model = value;
                }
                case "physical id" -> {
                    physical = value;
                    sockets.add(value);
                }
                case "core id" -> cores.add(physical + ":" + value);
                default -> {
                }
            }
        }
        return new Cpu(model == null ? "" : model.replaceAll("\\s+", " "), threads,
                cores.isEmpty() ? threads : cores.size(), Math.max(1, sockets.size()));
    }

    /** A line of {@code /proc/mounts} that holds a file system on a disk or medium. */
    public record Mount(String device, String mountPoint, String type) {
    }

    /**
     * The mounted disks and media from {@code /proc/mounts}: one entry per device (bind mounts left out), plus
     * the root directory whatever its type, since the live system runs from an overlay.
     */
    public static List<Mount> parseMounts(String text) {
        List<Mount> mounts = new ArrayList<>();
        Set<String> devices = new LinkedHashSet<>();
        for (String line : text.split("\n")) {
            String[] f = line.trim().split("\\s+");
            if (f.length < 3) continue;
            Mount mount = new Mount(unescape(f[0]), unescape(f[1]), f[2]);
            boolean disk = mount.device().startsWith("/dev/") && DISK_TYPES.contains(mount.type());
            if (mount.mountPoint().equals("/") || (disk && devices.add(mount.device()))) {
                mounts.removeIf(m -> m.mountPoint().equals(mount.mountPoint()));
                mounts.add(mount);
            }
        }
        return mounts;
    }

    /** Mount points escape blanks and the like as octal: {@code /media/My\040Disk}. */
    static String unescape(String field) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (c == '\\' && i + 3 < field.length() && field.substring(i + 1, i + 4).matches("[0-7]{3}")) {
                out.append((char) Integer.parseInt(field.substring(i + 1, i + 4), 8));
                i += 3;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** The graphics adapters in the output of {@code lspci -mm}: "vendor device" of the display controllers. */
    public static List<String> parseLspciGraphics(String text) {
        List<String> adapters = new ArrayList<>();
        for (String line : text.split("\n")) {
            List<String> f = quotedFields(line);
            if (f.size() < 4) continue;
            String kind = f.get(1).toLowerCase(Locale.ROOT);
            if (kind.contains("vga") || kind.contains("3d") || kind.contains("display")) {
                adapters.add((f.get(2) + " " + f.get(3)).trim());
            }
        }
        return adapters;
    }

    /** The fields of an {@code lspci -mm} line: the slot, then quoted strings. */
    static List<String> quotedFields(String line) {
        List<String> fields = new ArrayList<>();
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == ' ') {
                i++;
            } else if (c == '"') {
                int end = line.indexOf('"', i + 1);
                if (end < 0) end = line.length();
                fields.add(line.substring(i + 1, end));
                i = end + 1;
            } else {
                int end = line.indexOf(' ', i);
                if (end < 0) end = line.length();
                fields.add(line.substring(i, end));
                i = end;
            }
        }
        return fields;
    }

    /** The first number of {@code /proc/uptime}, in whole seconds. */
    public static Optional<Long> parseUptime(String text) {
        String[] f = text.trim().split("\\s+");
        try {
            return Optional.of((long) Double.parseDouble(f[0]));
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return Optional.empty();
        }
    }
}
