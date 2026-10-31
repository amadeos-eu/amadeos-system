package eu.amadeos.system.base.notify.dbus;

import org.freedesktop.dbus.Tuple;
import org.freedesktop.dbus.annotations.Position;

/**
 * The answer of {@code GetServerInformation}: name, vendor, version and the version of the specification. Generic,
 * because dbus-java reads the D-Bus types of a tuple's members from the type arguments of the method that returns
 * it ({@code ServerInformation<String, String, String, String>}).
 */
public final class ServerInformation<A, B, C, D> extends Tuple {
    @Position(0)
    private final A name;
    @Position(1)
    private final B vendor;
    @Position(2)
    private final C version;
    @Position(3)
    private final D specVersion;

    public ServerInformation(A name, B vendor, C version, D specVersion) {
        this.name = name;
        this.vendor = vendor;
        this.version = version;
        this.specVersion = specVersion;
    }

    public A getName() {
        return name;
    }

    public B getVendor() {
        return vendor;
    }

    public C getVersion() {
        return version;
    }

    public D getSpecVersion() {
        return specVersion;
    }
}
