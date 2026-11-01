package eu.amadeos.system.disks.model;

/**
 * One block of the partition map: a volume, or free space between partitions.
 *
 * @param offset where it starts, in bytes
 * @param size   its size in bytes
 * @param volume the partition or file system, null for free space
 */
public record Segment(long offset, long size, Volume volume) {
    public boolean free() {
        return volume == null;
    }
}
