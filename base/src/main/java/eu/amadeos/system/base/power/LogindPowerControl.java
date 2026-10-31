package eu.amadeos.system.base.power;

import eu.amadeos.system.base.power.dbus.LoginManager;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.Properties;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link PowerControl} on systemd-logind's D-Bus API on the system bus. The user of the active session at the
 * computer may suspend, hibernate and switch off without a password (polkit's defaults); with other users
 * logged in, polkit asks for an administrator's password.
 */
public final class LogindPowerControl implements PowerControl {
    private static final Logger LOGGER = Logger.getLogger(LogindPowerControl.class.getName());

    private DBusConnection bus;

    private synchronized DBusConnection bus() throws DBusException {
        if (bus == null || !bus.isConnected()) bus = DBusConnectionBuilder.forSystemBus().build();
        return bus;
    }

    private LoginManager manager() throws DBusException {
        return bus().getRemoteObject(LoginManager.BUS_NAME, LoginManager.PATH, LoginManager.class);
    }

    @Override
    public boolean canSuspend() {
        return answer("CanSuspend");
    }

    @Override
    public boolean canHibernate() {
        return answer("CanHibernate");
    }

    private boolean answer(String method) {
        try {
            LoginManager m = manager();
            String answer = "CanSuspend".equals(method) ? m.canSuspend() : m.canHibernate();
            return "yes".equals(answer) || "challenge".equals(answer);
        } catch (DBusException | DBusExecutionException e) {
            LOGGER.log(Level.FINE, "logind not available", e);
            return false;
        }
    }

    @Override
    public void suspend() throws PowerException {
        call("suspend", () -> manager().suspend(true));
    }

    @Override
    public void hibernate() throws PowerException {
        call("hibernate", () -> manager().hibernate(true));
    }

    @Override
    public void powerOff() throws PowerException {
        call("power off", () -> manager().powerOff(true));
    }

    @Override
    public boolean lidClosed() {
        return property("LidClosed");
    }

    @Override
    public boolean docked() {
        return property("Docked");
    }

    private boolean property(String name) {
        try {
            Properties p = bus().getRemoteObject(LoginManager.BUS_NAME, LoginManager.PATH, Properties.class);
            Object value = p.Get(LoginManager.NAME, name);
            return Boolean.TRUE.equals(value);
        } catch (DBusException | DBusExecutionException e) {
            LOGGER.log(Level.FINE, "logind property " + name + " not available", e);
            return false;
        }
    }

    @Override
    public AutoCloseable onSleep(Consumer<Boolean> listener) throws PowerException {
        try {
            DBusConnection connection = bus();
            AutoCloseable handler = connection.addSigHandler(LoginManager.PrepareForSleep.class,
                    signal -> listener.accept(signal.start()));
            return handler;
        } catch (DBusException | DBusExecutionException e) {
            throw new PowerException("logind not available: " + e.getMessage(), e);
        }
    }

    private interface Call {
        void run() throws DBusException;
    }

    private void call(String what, Call call) throws PowerException {
        try {
            call.run();
        } catch (DBusException | DBusExecutionException e) {
            throw new PowerException("Could not " + what + ": " + e.getMessage(), e);
        }
    }
}
