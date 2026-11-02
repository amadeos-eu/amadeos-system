package eu.amadeos.system.disks.udisks;

import eu.amadeos.system.disks.service.DiskException.Reason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UDisksServiceTest {
    @Test
    void errorsBecomeReasons() {
        assertEquals(Reason.CANCELLED, UDisksService.reason("org.freedesktop.UDisks2.Error.NotAuthorizedDismissed", ""));
        assertEquals(Reason.NOT_ALLOWED, UDisksService.reason("org.freedesktop.UDisks2.Error.NotAuthorizedCanObtain",
                ""));
        assertEquals(Reason.BUSY, UDisksService.reason("org.freedesktop.UDisks2.Error.DeviceBusy", ""));
        assertEquals(Reason.BUSY, UDisksService.reason("org.freedesktop.UDisks2.Error.Failed",
                "Error unmounting /dev/sdb1: target is busy"));
        assertEquals(Reason.UNAVAILABLE, UDisksService.reason("org.freedesktop.DBus.Error.ServiceUnknown", ""));
        assertEquals(Reason.FAILED, UDisksService.reason("org.freedesktop.UDisks2.Error.Failed", "mkfs failed"));
    }

    @Test
    void mountFolderNames() {
        assertEquals("Data", UDisksService.mountName("Data", "u"));
        assertEquals("Meine_Fotos", UDisksService.mountName(" Meine Fotos ", "u"));
        assertEquals("a1b2", UDisksService.mountName("../", "a1b2"));
        assertEquals("Größe", UDisksService.mountName("Größe", "u"));
    }
}
