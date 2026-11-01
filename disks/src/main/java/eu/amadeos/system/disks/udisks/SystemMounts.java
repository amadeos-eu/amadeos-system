package eu.amadeos.system.disks.udisks;

import eu.amadeos.system.disks.model.Safety;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * What the kernel says is mounted ({@code /proc/self/mountinfo}) and used as swap ({@code /proc/swaps}), as a
 * second opinion next to udisks: a device that holds the running system stays locked even when udisks does not
 * know its file system. Devices under a mapped system device (LUKS, LVM: {@code /sys/class/block/dm-N/slaves})
 * count as system devices too.
 */
public final class SystemMounts {
    public static final SystemMounts NONE = new SystemMounts(Map.of(), Map.of(), Set.of());

    private final Map<String, List<String>> mountPoints;
    private final Map<String, String> fsTypes;
    private final Set<String> system;

    SystemMounts(Map<String, List<String>> mountPoints, Map<String, String> fsTypes, Set<String> system) {
        this.mountPoints = mountPoints;
        this.fsTypes = fsTypes;
        this.system = system;
    }

    /** Reads the running system's tables. */
    public static SystemMounts read() {
        try {
            String mountinfo = Files.readString(Path.of("/proc/self/mountinfo"));
            String swaps = Files.exists(Path.of("/proc/swaps")) ? Files.readString(Path.of("/proc/swaps")) : "";
            return parse(mountinfo, swaps, SystemMounts::realDevice, SystemMounts::slaves);
        } catch (IOException e) {
            return NONE;
        }
    }

    /**
     * @param resolve turns a mount source (/dev/mapper/x, /dev/disk/by-uuid/...) into the kernel device (/dev/dm-0)
     * @param slaves  the devices under a device (/dev/dm-0 → /dev/nvme0n1p3)
     */
    static SystemMounts parse(String mountinfo, String swaps, Function<String, String> resolve,
            Function<String, List<String>> slaves) {
        Map<String, List<String>> points = new HashMap<>();
        Map<String, String> types = new HashMap<>();
        Set<String> system = new HashSet<>();
        for (String line : mountinfo.split("\n")) {
            String[] parts = line.split(" ");
            int dash = List.of(parts).indexOf("-");
            if (parts.length < 5 || dash < 0 || dash + 2 >= parts.length) continue;
            String source = parts[dash + 2];
            if (!source.startsWith("/dev/")) continue;
            String device = resolve.apply(source);
            String point = unescape(parts[4]);
            points.computeIfAbsent(device, k -> new ArrayList<>()).add(point);
            types.putIfAbsent(device, parts[dash + 1]);
            if (Safety.SYSTEM_MOUNTS.contains(point)) system.add(device);
        }
        for (String line : swaps.split("\n")) {
            if (line.startsWith("/dev/")) system.add(resolve.apply(line.split("\\s+")[0]));
        }
        List<String> todo = new ArrayList<>(system);
        while (!todo.isEmpty()) {
            for (String under : slaves.apply(todo.remove(todo.size() - 1))) {
                if (system.add(under)) todo.add(under);
            }
        }
        return new SystemMounts(points, types, system);
    }

    /** Where the kernel has this device mounted. */
    public List<String> mountPoints(String device) {
        return mountPoints.getOrDefault(device, List.of());
    }

    public String fsType(String device) {
        return fsTypes.getOrDefault(device, "");
    }

    /** It holds (part of) the running system. */
    public boolean system(String device) {
        return system.contains(device);
    }

    /** mountinfo writes spaces and tabs in paths as octal escapes. */
    static String unescape(String s) {
        return s.replace("\\040", " ").replace("\\011", "\t").replace("\\012", "\n").replace("\\134", "\\");
    }

    private static String realDevice(String source) {
        try {
            return Path.of(source).toRealPath().toString();
        } catch (IOException | RuntimeException e) {
            return source;
        }
    }

    private static List<String> slaves(String device) {
        Path dir = Path.of("/sys/class/block", Path.of(device).getFileName().toString(), "slaves");
        List<String> out = new ArrayList<>();
        try (var list = Files.list(dir)) {
            list.forEach(p -> out.add("/dev/" + p.getFileName()));
        } catch (IOException | RuntimeException e) {
            // no slaves
        }
        return out;
    }
}
