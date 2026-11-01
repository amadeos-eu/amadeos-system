package eu.amadeos.system.disks.udisks.dbus;

import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.UInt64;

/** {@code org.freedesktop.UDisks2.Manager}: what udisks can do (resizing which file systems). */
@DBusInterfaceName("org.freedesktop.UDisks2.Manager")
@SuppressWarnings("checkstyle:MethodName")
public interface Manager extends DBusInterface {
    String NAME = "org.freedesktop.UDisks2.Manager";

    /** Whether a file system type can be resized: available, the mode flags and a missing tool. */
    ResizeAbility CanResize(String type);

    /** {@code (bts)}: flags 2 offline shrink, 4 offline grow, 8 online shrink, 16 online grow. */
    final class ResizeAbility extends Struct {
        @Position(0)
        private final boolean available;
        @Position(1)
        private final UInt64 flags;
        @Position(2)
        private final String missing;

        public ResizeAbility(boolean available, UInt64 flags, String missing) {
            this.available = available;
            this.flags = flags;
            this.missing = missing;
        }

        public boolean isAvailable() {
            return available;
        }

        public long getFlags() {
            return flags.longValue();
        }

        public String getMissing() {
            return missing;
        }
    }
}
