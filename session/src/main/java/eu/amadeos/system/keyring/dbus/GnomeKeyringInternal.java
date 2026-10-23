package eu.amadeos.system.keyring.dbus;

import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusMemberName;
import org.freedesktop.dbus.interfaces.DBusInterface;

/**
 * gnome-keyring's own interface on {@code /org/freedesktop/secrets} for changing and unlocking a keyring with
 * passwords the caller asked for itself, without gnome-keyring's prompt (which needs GNOME's gcr prompter).
 */
@DBusInterfaceName("org.gnome.keyring.InternalUnsupportedGuiltRiddenInterface")
public interface GnomeKeyringInternal extends DBusInterface {
    @DBusMemberName("ChangeWithMasterPassword")
    void changeWithMasterPassword(DBusPath collection, Secret original, Secret master);

    @DBusMemberName("UnlockWithMasterPassword")
    void unlockWithMasterPassword(DBusPath collection, Secret master);
}
