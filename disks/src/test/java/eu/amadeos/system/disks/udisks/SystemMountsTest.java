package eu.amadeos.system.disks.udisks;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemMountsTest {
    static final String MOUNTINFO = """
            22 1 259:2 / / rw,relatime shared:1 - ext4 /dev/mapper/root rw
            23 22 0:5 / /dev rw - devtmpfs udev rw
            30 22 259:1 / /boot/efi rw,relatime shared:5 - vfat /dev/nvme0n1p1 rw
            41 22 8:17 / /media/ralf/MY\\040STICK rw,nosuid - vfat /dev/sdb1 rw
            """;
    static final String SWAPS = """
            Filename				Type		Size		Used		Priority
            /dev/nvme0n1p3                          partition	16777212	0		-2
            """;

    @Test
    void readsMountsSwapAndTheDevicesUnderAMappedRoot() {
        Map<String, String> real = Map.of("/dev/mapper/root", "/dev/dm-0");
        Map<String, List<String>> slaves = Map.of("/dev/dm-0", List.of("/dev/nvme0n1p2"));
        SystemMounts m = SystemMounts.parse(MOUNTINFO, SWAPS, s -> real.getOrDefault(s, s),
                d -> slaves.getOrDefault(d, List.of()));
        assertEquals(List.of("/"), m.mountPoints("/dev/dm-0"));
        assertEquals(List.of("/media/ralf/MY STICK"), m.mountPoints("/dev/sdb1"));
        assertEquals("vfat", m.fsType("/dev/sdb1"));
        assertTrue(m.system("/dev/dm-0"));
        assertTrue(m.system("/dev/nvme0n1p2"), "the LUKS or LVM partition under /");
        assertTrue(m.system("/dev/nvme0n1p1"), "/boot/efi");
        assertTrue(m.system("/dev/nvme0n1p3"), "active swap");
        assertFalse(m.system("/dev/sdb1"));
        assertEquals(List.of(), m.mountPoints("/dev/sdc1"));
    }
}
