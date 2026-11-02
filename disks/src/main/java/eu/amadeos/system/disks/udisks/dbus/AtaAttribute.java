package eu.amadeos.system.disks.udisks.dbus;

import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.types.UInt16;
import org.freedesktop.dbus.types.Variant;

import java.util.Map;

/** One SMART attribute of a SATA drive: {@code (ysqiiixia{sv})}. */
public final class AtaAttribute extends Struct {
    @Position(0)
    private final byte id;
    @Position(1)
    private final String name;
    @Position(2)
    private final UInt16 flags;
    @Position(3)
    private final int value;
    @Position(4)
    private final int worst;
    @Position(5)
    private final int threshold;
    @Position(6)
    private final long pretty;
    @Position(7)
    private final int prettyUnit;
    @Position(8)
    private final Map<String, Variant<?>> expansion;

    public AtaAttribute(byte id, String name, UInt16 flags, int value, int worst, int threshold, long pretty,
            int prettyUnit, Map<String, Variant<?>> expansion) {
        this.id = id;
        this.name = name;
        this.flags = flags;
        this.value = value;
        this.worst = worst;
        this.threshold = threshold;
        this.pretty = pretty;
        this.prettyUnit = prettyUnit;
        this.expansion = expansion;
    }

    public int getId() {
        return id & 0xFF;
    }

    public String getName() {
        return name;
    }

    public int getFlags() {
        return flags.intValue();
    }

    public int getValue() {
        return value;
    }

    public int getWorst() {
        return worst;
    }

    public int getThreshold() {
        return threshold;
    }

    public long getPretty() {
        return pretty;
    }

    /** 0 unknown, 1 dimensionless, 2 milliseconds, 3 sectors, 4 millikelvin. */
    public int getPrettyUnit() {
        return prettyUnit;
    }
}
