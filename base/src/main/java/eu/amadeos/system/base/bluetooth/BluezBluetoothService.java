package eu.amadeos.system.base.bluetooth;

import eu.amadeos.system.base.bluetooth.dbus.Adapter1;
import eu.amadeos.system.base.bluetooth.dbus.Agent1;
import eu.amadeos.system.base.bluetooth.dbus.AgentManager1;
import eu.amadeos.system.base.bluetooth.dbus.Device1;
import org.bluez.Error;
import org.freedesktop.dbus.DBusAsyncReply;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.ObjectManager;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.UInt16;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link BluetoothService} on BlueZ's D-Bus API on the system bus. While pairing, an agent object of this process
 * answers bluetoothd's questions (numbers to compare, PINs) through a {@link PairingAgent}. Users in the group
 * {@code bluetooth} may do all of this without a password.
 */
public final class BluezBluetoothService implements BluetoothService {
    private static final Logger LOGGER = Logger.getLogger(BluezBluetoothService.class.getName());
    private static final String BUS_NAME = "org.bluez";
    private static final String AGENT_PATH = "/eu/amadeos/settings/bluetooth_agent";
    private static final long PAIR_TIMEOUT_MILLIS = 90_000;
    private static final long CONNECT_TIMEOUT_MILLIS = 30_000;

    private DBusConnection bus;

    private synchronized DBusConnection bus() throws DBusException {
        if (bus == null || !bus.isConnected()) bus = DBusConnectionBuilder.forSystemBus().build();
        return bus;
    }

    @Override
    public BluetoothStatus status() {
        try {
            ObjectManager objects = bus().getRemoteObject(BUS_NAME, "/", ObjectManager.class);
            return BluezObjects.status(objects.GetManagedObjects());
        } catch (DBusException | DBusExecutionException e) {
            LOGGER.log(Level.FINE, "BlueZ not available", e);
            return BluetoothStatus.UNAVAILABLE;
        }
    }

    @Override
    public void setPowered(boolean on) throws BluetoothException {
        setAdapterProperty("Powered", on);
    }

    @Override
    public void setDiscoverable(boolean on) throws BluetoothException {
        setAdapterProperty("Discoverable", on);
    }

    @Override
    public void setSearching(boolean on) throws BluetoothException {
        try {
            Adapter1 adapter = bus().getRemoteObject(BUS_NAME, adapterPath(), Adapter1.class);
            if (on) adapter.startDiscovery();
            else adapter.stopDiscovery();
        } catch (DBusException | DBusExecutionException e) {
            // Stopping a search another program started, or one that already ended, is no error for the user.
            if (on) throw new BluetoothException(message(e));
        }
    }

    // --8<-- [start:pair]
    @Override
    public void pair(BluetoothDevice device, PairingAgent agent) throws BluetoothException {
        DBusPath agentPath = new DBusPath(AGENT_PATH);
        try {
            DBusConnection connection = bus();
            connection.exportObject(AGENT_PATH, new Agent(agent, this::find));
            AgentManager1 manager = connection.getRemoteObject(BUS_NAME, AgentManager1.PATH, AgentManager1.class);
            manager.registerAgent(agentPath, "KeyboardDisplay");
            try {
                Device1 remote = connection.getRemoteObject(BUS_NAME, device.id(), Device1.class);
                await(connection.callMethodAsync(remote, "Pair"), PAIR_TIMEOUT_MILLIS, () -> remote.CancelPairing());
                // Trusted devices may connect by themselves later, e.g. headphones switched on.
                setProperty(device.id(), BluezObjects.DEVICE, "Trusted", true);
                await(connection.callMethodAsync(remote, "Connect"), CONNECT_TIMEOUT_MILLIS, () -> { });
            } finally {
                agent.done();
                try {
                    manager.unregisterAgent(agentPath);
                } catch (DBusExecutionException e) {
                    LOGGER.log(Level.FINE, "Agent already gone", e);
                }
                connection.unExportObject(AGENT_PATH);
            }
        } catch (DBusException | DBusExecutionException e) {
            throw new BluetoothException(message(e));
        }
    }
    // --8<-- [end:pair]

    @Override
    public void connect(BluetoothDevice device) throws BluetoothException {
        try {
            DBusConnection connection = bus();
            Device1 remote = connection.getRemoteObject(BUS_NAME, device.id(), Device1.class);
            await(connection.callMethodAsync(remote, "Connect"), CONNECT_TIMEOUT_MILLIS, () -> { });
        } catch (DBusException | DBusExecutionException e) {
            throw new BluetoothException(message(e));
        }
    }

    @Override
    public void disconnect(BluetoothDevice device) throws BluetoothException {
        try {
            bus().getRemoteObject(BUS_NAME, device.id(), Device1.class).Disconnect();
        } catch (DBusException | DBusExecutionException e) {
            throw new BluetoothException(message(e));
        }
    }

    @Override
    public void remove(BluetoothDevice device) throws BluetoothException {
        try {
            bus().getRemoteObject(BUS_NAME, adapterPath(), Adapter1.class).removeDevice(new DBusPath(device.id()));
        } catch (DBusException | DBusExecutionException e) {
            throw new BluetoothException(message(e));
        }
    }

    @Override
    public AutoCloseable watch(Runnable onChange) {
        List<AutoCloseable> handlers = new ArrayList<>();
        try {
            DBusConnection connection = bus();
            handlers.add(connection.addSigHandler(Properties.PropertiesChanged.class, signal -> {
                if (signal.getInterfaceName().startsWith("org.bluez.")) onChange.run();
            }));
            handlers.add(connection.addSigHandler(ObjectManager.InterfacesAdded.class, signal -> onChange.run()));
            handlers.add(connection.addSigHandler(ObjectManager.InterfacesRemoved.class, signal -> onChange.run()));
        } catch (DBusException e) {
            LOGGER.log(Level.FINE, "Cannot follow BlueZ", e);
        }
        return () -> {
            for (AutoCloseable h : handlers) h.close();
        };
    }

    private String adapterPath() throws BluetoothException {
        BluetoothStatus status = status();
        if (!status.hasAdapter()) throw new BluetoothException("no Bluetooth adapter");
        return status.adapter().id();
    }

    private void setAdapterProperty(String name, boolean value) throws BluetoothException {
        try {
            setProperty(adapterPath(), BluezObjects.ADAPTER, name, value);
        } catch (DBusException | DBusExecutionException e) {
            throw new BluetoothException(message(e));
        }
    }

    private void setProperty(String path, String iface, String name, boolean value) throws DBusException {
        bus().getRemoteObject(BUS_NAME, path, Properties.class).Set(iface, name, new Variant<>(value));
    }

    private BluetoothDevice find(DBusPath path) {
        return status().devices().stream().filter(d -> d.id().equals(path.getPath())).findFirst()
                .orElse(new BluetoothDevice(path.getPath(), "", path.getPath(), BluetoothDevice.Kind.OTHER, false,
                        false, -1, 0));
    }

    /** Waits for an asynchronous call; on timeout {@code cancel} runs and the call counts as failed. */
    private static void await(DBusAsyncReply<?> reply, long timeoutMillis, Runnable cancel) throws BluetoothException {
        long end = System.currentTimeMillis() + timeoutMillis;
        while (!reply.hasReply()) {
            if (System.currentTimeMillis() > end) {
                cancel.run();
                throw new BluetoothException("timeout");
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancel.run();
                throw new BluetoothException("interrupted");
            }
        }
        try {
            reply.getReply();
        } catch (DBusException | DBusExecutionException e) {
            throw new BluetoothException(message(e));
        }
    }

    /** BlueZ's reason, e.g. "Authentication Failed" or "Page Timeout", without the error name. */
    static String message(Exception e) {
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m.strip();
    }

    /** The agent object bluetoothd calls while this process pairs. */
    private static final class Agent implements Agent1 {
        private final PairingAgent ui;
        private final java.util.function.Function<DBusPath, BluetoothDevice> devices;

        Agent(PairingAgent ui, java.util.function.Function<DBusPath, BluetoothDevice> devices) {
            this.ui = ui;
            this.devices = devices;
        }

        @Override
        public void release() {
        }

        @Override
        public String requestPinCode(DBusPath device) {
            String pin = ui.requestPin(devices.apply(device));
            if (pin == null) throw new Error.Canceled("cancelled");
            return pin;
        }

        @Override
        public void displayPinCode(DBusPath device, String pinCode) {
            ui.showPasskey(devices.apply(device), pinCode);
        }

        @Override
        public UInt32 requestPasskey(DBusPath device) {
            String pin = ui.requestPin(devices.apply(device));
            if (pin == null) throw new Error.Canceled("cancelled");
            try {
                return new UInt32(Long.parseLong(pin.strip()));
            } catch (NumberFormatException e) {
                throw new Error.Rejected("not a number");
            }
        }

        @Override
        public void displayPasskey(DBusPath device, UInt32 passkey, UInt16 entered) {
            ui.showPasskey(devices.apply(device), String.format("%06d", passkey.longValue()));
        }

        @Override
        public void requestConfirmation(DBusPath device, UInt32 passkey) {
            if (!ui.confirm(devices.apply(device), String.format("%06d", passkey.longValue()))) {
                throw new Error.Rejected("numbers differ");
            }
        }

        @Override
        public void requestAuthorization(DBusPath device) {
            // "Just works" pairing started from this page: the user asked for it already.
        }

        @Override
        public void authorizeService(DBusPath device, String uuid) {
        }

        @Override
        public void cancel() {
            ui.done();
        }

        @Override
        public String getObjectPath() {
            return AGENT_PATH;
        }
    }
}
