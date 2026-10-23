package eu.amadeos.system.keyring;

import eu.amadeos.system.keyring.dbus.GnomeKeyringInternal;
import eu.amadeos.system.keyring.dbus.Secret;
import eu.amadeos.system.keyring.dbus.SecretService;
import eu.amadeos.system.keyring.dbus.SessionClose;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.Variant;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * The login keyring through the Secret Service API on the session bus ({@code org.freedesktop.secrets},
 * gnome-keyring on amadeOS). Passwords travel in a "plain" session: the session bus connects two processes of
 * the same user on the same machine. Each call opens its own connection, so a page that is open for long does
 * not keep the bus busy.
 */
public final class SecretServiceKeyring implements Keyring {
    private static final String COLLECTION = "org.freedesktop.Secret.Collection";
    private static final String NO_OBJECT = "/";

    @Override
    public Status status() {
        try (DBusConnection bus = connect()) {
            DBusPath collection = defaultCollection(bus);
            if (collection == null) return Status.NONE;
            Properties props = bus.getRemoteObject(SecretService.BUS_NAME, collection.getPath(), Properties.class);
            boolean locked = props.<Boolean>Get(COLLECTION, "Locked");
            List<?> items = props.<List<?>>Get(COLLECTION, "Items");
            return new Status(true, locked, items.size());
        } catch (DBusException | DBusExecutionException | IOException e) {
            return Status.NONE;
        }
    }

    @Override
    public void lock() throws IOException {
        try (DBusConnection bus = connect()) {
            DBusPath collection = requireCollection(bus);
            service(bus).lock(List.of(collection));
        } catch (DBusException | DBusExecutionException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public void unlock(char[] password) throws IOException {
        try (DBusConnection bus = connect()) {
            DBusPath collection = requireCollection(bus);
            DBusPath session = openPlainSession(bus);
            byte[] secret = utf8(password);
            try {
                internal(bus).unlockWithMasterPassword(collection, Secret.password(session, secret));
            } finally {
                Arrays.fill(secret, (byte) 0);
                closeSession(bus, session);
            }
        } catch (DBusException | DBusExecutionException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public void changePassword(char[] current, char[] newPassword) throws IOException {
        try (DBusConnection bus = connect()) {
            DBusPath collection = requireCollection(bus);
            DBusPath session = openPlainSession(bus);
            byte[] original = utf8(current);
            byte[] master = utf8(newPassword);
            try {
                internal(bus).changeWithMasterPassword(collection, Secret.password(session, original),
                        Secret.password(session, master));
            } finally {
                Arrays.fill(original, (byte) 0);
                Arrays.fill(master, (byte) 0);
                closeSession(bus, session);
            }
        } catch (DBusException | DBusExecutionException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    private static DBusConnection connect() throws DBusException {
        return DBusConnectionBuilder.forSessionBus().withShared(false).build();
    }

    private static SecretService service(DBusConnection bus) throws DBusException {
        return bus.getRemoteObject(SecretService.BUS_NAME, SecretService.PATH, SecretService.class);
    }

    private static GnomeKeyringInternal internal(DBusConnection bus) throws DBusException {
        return bus.getRemoteObject(SecretService.BUS_NAME, SecretService.PATH, GnomeKeyringInternal.class);
    }

    /** The keyring the alias "default" points to, the login keyring; null if there is none. */
    private static DBusPath defaultCollection(DBusConnection bus) throws DBusException {
        DBusPath path = service(bus).readAlias("default");
        return path == null || NO_OBJECT.equals(path.getPath()) ? null : path;
    }

    private static DBusPath requireCollection(DBusConnection bus) throws DBusException, IOException {
        DBusPath collection = defaultCollection(bus);
        if (collection == null) throw new IOException("There is no login keyring");
        return collection;
    }

    private static DBusPath openPlainSession(DBusConnection bus) throws DBusException {
        return service(bus).openSession("plain", new Variant<>("")).result;
    }

    private static void closeSession(DBusConnection bus, DBusPath session) {
        try {
            bus.getRemoteObject(SecretService.BUS_NAME, session.getPath(), SessionClose.class).close();
        } catch (DBusException | DBusExecutionException e) {
            // The session ends with the connection anyway.
        }
    }

    private static byte[] utf8(char[] password) {
        ByteBuffer buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        Arrays.fill(buffer.array(), (byte) 0);
        return bytes;
    }
}
