package org.datazuul.amadeos.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link NetworkService} on top of NetworkManager's command-line client. Permission to change connections
 * comes from polkit (/etc/polkit-1/rules.d/50-amadeos-network.rules grants it to the netdev group).
 */
public final class NmcliNetworkService implements NetworkService {
    private static final Logger LOGGER = Logger.getLogger(NmcliNetworkService.class.getName());
    private static final long TIMEOUT_SECONDS = 60;

    @Override
    public NetworkStatus status() {
        try {
            List<NetworkStatus.Device> devices = Nmcli.parseDevices(run(Nmcli.deviceStatus()));
            boolean wifiEnabled = Nmcli.parseRadio(run(Nmcli.wifiRadio()));
            int signal = 0;
            if (devices.stream().anyMatch(d -> d.type().equals("wifi") && d.connected())) {
                signal = Nmcli.parseWifiList(run(Nmcli.wifiList(false)), Set.of()).stream()
                        .filter(WifiNetwork::active).mapToInt(WifiNetwork::signal).findFirst().orElse(0);
            }
            return NetworkStatus.of(devices, wifiEnabled, signal);
        } catch (IOException | NetworkException e) {
            LOGGER.log(Level.FINE, "NetworkManager not available", e);
            return NetworkStatus.UNAVAILABLE;
        }
    }

    @Override
    public List<WifiNetwork> wifiNetworks(boolean rescan) {
        try {
            Set<String> known = Nmcli.parseWifiConnections(run(Nmcli.connections()));
            return Nmcli.parseWifiList(run(Nmcli.wifiList(rescan)), known);
        } catch (IOException | NetworkException e) {
            LOGGER.log(Level.FINE, "Could not list WLAN networks", e);
            return List.of();
        }
    }

    @Override
    public void connect(WifiNetwork network, String password) throws NetworkException {
        String device = status().wifiDevice();
        try {
            run(Nmcli.connect(network, password, device));
        } catch (NetworkException e) {
            // A refused password would otherwise leave a saved connection that fails again at every start
            if (!network.known()) {
                tryRun(Nmcli.forget(network.ssid()));
            }
            throw e;
        } catch (IOException e) {
            throw new NetworkException(e.getMessage());
        }
    }

    @Override
    public void disconnectWifi() throws NetworkException {
        String device = status().wifiDevice();
        if (device != null) runOrThrow(Nmcli.disconnect(device));
    }

    @Override
    public void forget(WifiNetwork network) throws NetworkException {
        runOrThrow(Nmcli.forget(network.ssid()));
    }

    @Override
    public void setWifiEnabled(boolean enabled) throws NetworkException {
        runOrThrow(Nmcli.setWifiRadio(enabled));
    }

    @Override
    public AutoCloseable watch(Runnable onChange) {
        Watcher watcher = new Watcher(onChange);
        Thread thread = new Thread(watcher, "nmcli-monitor");
        thread.setDaemon(true);
        thread.start();
        return watcher;
    }

    /** Follows {@code nmcli monitor} and restarts it if it ends, e.g. when NetworkManager restarts. */
    private static final class Watcher implements Runnable, AutoCloseable {
        private final Runnable onChange;
        private volatile boolean closed;
        private volatile Process process;

        Watcher(Runnable onChange) {
            this.onChange = onChange;
        }

        @Override
        public void run() {
            while (!closed) {
                try {
                    process = start(Nmcli.monitor());
                    process.getOutputStream().close();
                    try (BufferedReader out = new BufferedReader(
                            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                        while (!closed && out.readLine() != null) {
                            onChange.run();
                        }
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.FINE, "nmcli monitor failed", e);
                }
                if (closed) return;
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException e) {
                    return;
                }
                onChange.run();
            }
        }

        @Override
        public void close() {
            closed = true;
            Process p = process;
            if (p != null) p.destroy();
        }
    }

    private void runOrThrow(List<String> command) throws NetworkException {
        try {
            run(command);
        } catch (IOException e) {
            throw new NetworkException(e.getMessage());
        }
    }

    private static void tryRun(List<String> command) {
        try {
            run(command);
        } catch (IOException | NetworkException e) {
            LOGGER.log(Level.FINE, "Ignored: " + command.get(1), e);
        }
    }

    /** Runs nmcli and returns its standard output; a non-zero exit becomes a NetworkException. */
    private static String run(List<String> command) throws IOException, NetworkException {
        Process process = start(command);
        process.getOutputStream().close();
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()));
        String stdout = read(process.getInputStream());
        try {
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new NetworkException("nmcli did not answer");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new NetworkException("interrupted");
        }
        if (process.exitValue() != 0) {
            throw new NetworkException(Nmcli.errorMessage(stderr.join()));
        }
        return stdout;
    }

    private static Process start(List<String> command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        // Untranslated states ("connected") with UTF-8 SSIDs left intact
        builder.environment().put("LC_ALL", "C.UTF-8");
        return builder.start();
    }

    private static String read(java.io.InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
