package eu.amadeos.system.disks.udisks.dbus;

import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.types.Variant;

import java.util.Map;

/** One {@code (sa{sv})} entry of a block's Configuration: {@code fstab} or {@code crypttab} and its fields. */
public final class ConfigurationItem extends Struct {
    @Position(0)
    private final String type;
    @Position(1)
    private final Map<String, Variant<?>> details;

    public ConfigurationItem(String type, Map<String, Variant<?>> details) {
        this.type = type;
        this.details = details;
    }

    public String getType() {
        return type;
    }

    public Map<String, Variant<?>> getDetails() {
        return details;
    }
}
