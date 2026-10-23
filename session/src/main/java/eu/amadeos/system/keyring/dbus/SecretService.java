package eu.amadeos.system.keyring.dbus;

import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.Tuple;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusMemberName;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.Variant;

import java.util.List;

/** The part of the Secret Service ({@code org.freedesktop.secrets} on the session bus) the Settings use. */
@DBusInterfaceName("org.freedesktop.Secret.Service")
public interface SecretService extends DBusInterface {
    String BUS_NAME = "org.freedesktop.secrets";
    String PATH = "/org/freedesktop/secrets";

    @DBusMemberName("OpenSession")
    SessionResult openSession(String algorithm, Variant<?> input);

    @DBusMemberName("ReadAlias")
    DBusPath readAlias(String name);

    @DBusMemberName("Lock")
    PathsAndPrompt lock(List<DBusPath> objects);

    /** {@code (v output, o result)} of OpenSession. */
    final class SessionResult extends Tuple {
        @Position(0)
        public final Variant<?> output;
        @Position(1)
        public final DBusPath result;

        public SessionResult(Variant<?> output, DBusPath result) {
            this.output = output;
            this.result = result;
        }
    }

    /** {@code (ao objects, o prompt)} of Lock and Unlock. */
    final class PathsAndPrompt extends Tuple {
        @Position(0)
        public final List<DBusPath> objects;
        @Position(1)
        public final DBusPath prompt;

        public PathsAndPrompt(List<DBusPath> objects, DBusPath prompt) {
            this.objects = objects;
            this.prompt = prompt;
        }
    }
}
