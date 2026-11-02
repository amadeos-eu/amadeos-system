package eu.amadeos.system.disks.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Builds the partition map of a drive: its partitions and the free space between them. */
public final class Layout {
    /** Partition tools start the first partition at 1 MiB and align to MiB; less free space is no space. */
    public static final long ALIGN = Sizes.MIB;
    /** Free space below this is not shown (alignment gaps, the GPT backup at the end). */
    public static final long MIN_FREE = 2 * Sizes.MIB;

    private Layout() {
    }

    /**
     * The map of a drive of {@code size} bytes with this partition table type and these partitions. A drive
     * without table shows its whole-drive file system, or one free block.
     *
     * @param top partitions on the top level of the table (primary ones and the extended container)
     * @param logical partitions inside the extended container
     */
    public static List<Segment> segments(long size, String tableType, List<Volume> top, List<Volume> logical,
            Volume whole) {
        List<Segment> out = new ArrayList<>();
        if (size <= 0) return out;
        if (tableType == null || tableType.isEmpty()) {
            out.add(new Segment(0, size, whole));
            return out;
        }
        // The usable area: from 1 MiB to the end, less 1 MiB for the GPT backup header (and alignment).
        long start = ALIGN;
        long end = "gpt".equals(tableType) ? size - ALIGN : size;
        List<Volume> parts = new ArrayList<>(top);
        parts.sort(Comparator.comparingLong(Volume::offset));
        long at = start;
        for (Volume p : parts) {
            if (p.offset() - at >= MIN_FREE) out.add(new Segment(at, p.offset() - at, null));
            if (isContainer(p, logical)) {
                out.addAll(inside(p, logical));
            } else {
                out.add(new Segment(p.offset(), p.size(), p));
            }
            at = Math.max(at, p.offset() + p.size());
        }
        if (end - at >= MIN_FREE) out.add(new Segment(at, end - at, null));
        return out;
    }

    private static boolean isContainer(Volume p, List<Volume> logical) {
        return logical.stream().anyMatch(l -> l.offset() >= p.offset() && l.offset() < p.offset() + p.size())
                || "extended".equals(p.type());
    }

    /** The logical partitions of an extended container; its free space is not offered (MBR only, rare). */
    private static List<Segment> inside(Volume container, List<Volume> logical) {
        List<Segment> out = new ArrayList<>();
        logical.stream()
                .filter(l -> l.offset() >= container.offset() && l.offset() < container.offset() + container.size())
                .sorted(Comparator.comparingLong(Volume::offset))
                .forEach(l -> out.add(new Segment(l.offset(), l.size(), l)));
        return out;
    }

    /** Rounds down to whole MiB, as partitions are aligned. */
    public static long alignDown(long bytes) {
        return bytes / ALIGN * ALIGN;
    }
}
