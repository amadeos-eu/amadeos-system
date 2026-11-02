package eu.amadeos.system.disks.service;

import eu.amadeos.system.disks.model.Drive;
import eu.amadeos.system.disks.model.FsChoice;
import eu.amadeos.system.disks.model.Health;
import eu.amadeos.system.disks.model.Volume;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * What Disks does with drives. {@link UDisksService} does it through udisks2 on the system bus, which asks the
 * administrator's password where needed; the tests and the pictures of the handbook use a fake one.
 * All methods block and are called off the event thread.
 */
public interface DiskService {

    /** All drives, in list order. */
    List<Drive> drives() throws DiskException;

    /** Calls {@code onChange} (on any thread) when drives come, go or change; closing stops it. */
    AutoCloseable watch(Runnable onChange);

    /** How far the running udisks job on this block device is, or {@link Progress#UNKNOWN}. */
    Progress progress(String blockId);

    /**
     * Writes a new partition table with one partition that fills the drive, formatted as chosen.
     *
     * @param overwrite  write zeros over the whole drive first
     * @param passphrase for {@link FsChoice#ENCRYPTED}, else null
     */
    void formatDrive(Drive drive, FsChoice choice, String label, boolean overwrite, char[] passphrase)
            throws DiskException;

    /** Formats one partition (or the file system on a drive without partition table). */
    void formatVolume(Volume volume, FsChoice choice, String label, boolean overwrite, char[] passphrase)
            throws DiskException;

    /**
     * Creates a partition in free space and formats it.
     *
     * @param mountAtStart adds an fstab line so it is mounted at /mnt/NAME at every start
     */
    void createPartition(Drive drive, long offset, long size, FsChoice choice, String label, char[] passphrase,
            boolean mountAtStart) throws DiskException;

    void deletePartition(Volume volume) throws DiskException;

    /** Grows or shrinks a partition with its file system to {@code size} bytes. */
    void resize(Volume volume, long size) throws DiskException;

    /** Bytes in use on a file system, mounting it for a moment if needed; -1 if not known. */
    long usedBytes(Volume volume) throws DiskException;

    /** Whether the file system type can be grown and shrunk by udisks: {grow, shrink}. */
    boolean[] canResize(String fsType);

    void setLabel(Volume volume, String label) throws DiskException;

    /** Mounts it and returns where. */
    String mount(Volume volume) throws DiskException;

    void unmount(Volume volume) throws DiskException;

    /** Checks the file system (unmounting it for the time); true when it has no errors. */
    boolean check(Volume volume) throws DiskException;

    /** Repairs the file system; true when it is fine afterwards. */
    boolean repair(Volume volume) throws DiskException;

    void unlock(Volume volume, char[] passphrase) throws DiskException;

    void lock(Volume volume) throws DiskException;

    /** Unmounts everything on it and switches it off, so it can be pulled out. */
    void powerOff(Drive drive) throws DiskException;

    void eject(Drive drive) throws DiskException;

    /** Reads the drive's health; {@code refresh} asks the drive anew instead of the last values of udisks. */
    Health health(Drive drive, boolean refresh) throws DiskException;

    /** Starts a self-test: short (minutes) or long (hours). */
    void startSelfTest(Drive drive, boolean longTest) throws DiskException;

    /** Opens the whole drive for writing an image to it (unmounts it first). */
    OutputStream openForRestore(Drive drive) throws DiskException;

    /** Opens the whole drive for reading it into an image. */
    InputStream openForBackup(Drive drive) throws DiskException;
}
