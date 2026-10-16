package org.datazuul.amadeos.net;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The {@code nmcli} command lines the desktop uses and parsers for their terse ({@code -t}) output. Pure, so
 * it can be tested without NetworkManager.
 * <p>
 * In terse mode nmcli separates fields with ':' and escapes ':' and '\' inside values with a backslash.
 */
public final class Nmcli {
    /** Seconds nmcli waits for an activation before giving up. */
    static final String WAIT_SECONDS = "45";

    private Nmcli() {
    }

    public static List<String> deviceStatus() {
        return List.of("nmcli", "-t", "-f", "DEVICE,TYPE,STATE,CONNECTION", "device", "status");
    }

    public static List<String> wifiList(boolean rescan) {
        return List.of("nmcli", "-t", "-f", "IN-USE,SSID,SIGNAL,SECURITY", "device", "wifi", "list",
                "--rescan", rescan ? "yes" : "no");
    }

    public static List<String> connections() {
        return List.of("nmcli", "-t", "-f", "NAME,TYPE", "connection", "show");
    }

    public static List<String> wifiRadio() {
        return List.of("nmcli", "-t", "radio", "wifi");
    }

    public static List<String> setWifiRadio(boolean on) {
        return List.of("nmcli", "radio", "wifi", on ? "on" : "off");
    }

    public static List<String> monitor() {
        return List.of("nmcli", "monitor");
    }

    /**
     * Joins a network. A saved connection is brought up again, so its stored password is used; otherwise
     * nmcli creates a connection named after the SSID, which NetworkManager saves and reconnects to at the
     * next start.
     * <p>
     * The password is an argument, so it is visible in the process list while nmcli runs. A D-Bus client
     * could hand it over without that; on a single-user desktop the window is a few seconds.
     */
    public static List<String> connect(WifiNetwork network, String password, String device) {
        if (network.known()) {
            return List.of("nmcli", "--wait", WAIT_SECONDS, "connection", "up", "id", network.ssid());
        }
        List<String> args = new ArrayList<>(List.of("nmcli", "--wait", WAIT_SECONDS, "device", "wifi", "connect",
                network.ssid()));
        if (network.secured()) {
            args.add("password");
            args.add(password == null ? "" : password);
        }
        if (device != null) {
            args.add("ifname");
            args.add(device);
        }
        return List.copyOf(args);
    }

    public static List<String> disconnect(String device) {
        return List.of("nmcli", "device", "disconnect", device);
    }

    public static List<String> forget(String connectionName) {
        return List.of("nmcli", "connection", "delete", "id", connectionName);
    }

    /** Splits one line of terse output into its unescaped fields. */
    public static List<String> fields(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                field.append(line.charAt(++i));
            } else if (c == ':') {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        return fields;
    }

    public static List<NetworkStatus.Device> parseDevices(String output) {
        List<NetworkStatus.Device> devices = new ArrayList<>();
        for (String line : output.lines().toList()) {
            List<String> f = fields(line);
            if (f.size() >= 4) {
                devices.add(new NetworkStatus.Device(f.get(0), f.get(1), f.get(2), f.get(3)));
            }
        }
        return devices;
    }

    /** Names of the saved WLAN connections. */
    public static Set<String> parseWifiConnections(String output) {
        Set<String> names = new java.util.HashSet<>();
        for (String line : output.lines().toList()) {
            List<String> f = fields(line);
            if (f.size() >= 2 && f.get(1).equals("802-11-wireless")) {
                names.add(f.get(0));
            }
        }
        return names;
    }

    public static boolean parseRadio(String output) {
        return output.strip().equals("enabled");
    }

    /**
     * Networks in range, one entry per SSID (the strongest access point), the active one first and then by
     * signal strength. Hidden networks (empty SSID) are left out.
     */
    public static List<WifiNetwork> parseWifiList(String output, Set<String> known) {
        Map<String, WifiNetwork> bySsid = new LinkedHashMap<>();
        for (String line : output.lines().toList()) {
            List<String> f = fields(line);
            if (f.size() < 4 || f.get(1).isEmpty()) continue;
            String ssid = f.get(1);
            boolean active = f.get(0).strip().equals("*");
            int signal = parseInt(f.get(2));
            String security = f.get(3).strip();
            boolean secured = !security.isEmpty() && !security.equals("--");
            WifiNetwork network = new WifiNetwork(ssid, signal, secured, active, known.contains(ssid));
            bySsid.merge(ssid, network, (a, b) -> new WifiNetwork(ssid, Math.max(a.signal(), b.signal()),
                    a.secured() || b.secured(), a.active() || b.active(), a.known()));
        }
        return bySsid.values().stream()
                .sorted(Comparator.comparing(WifiNetwork::active).reversed()
                        .thenComparing(Comparator.comparingInt(WifiNetwork::signal).reversed())
                        .thenComparing(WifiNetwork::ssid, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** nmcli's error text without the "Error: " prefix, for showing to the user. */
    public static String errorMessage(String stderr) {
        String message = stderr.strip();
        int last = message.lastIndexOf("Error: ");
        if (last >= 0) message = message.substring(last + "Error: ".length());
        return message.lines().findFirst().orElse("").strip();
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
