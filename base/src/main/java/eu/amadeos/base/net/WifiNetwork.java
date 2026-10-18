package eu.amadeos.base.net;

/**
 * A WLAN network in range.
 *
 * @param ssid    network name
 * @param signal  signal strength 0-100
 * @param secured whether joining needs a password (WEP, WPA, WPA2, WPA3)
 * @param active  whether this computer is connected to it
 * @param known   whether a saved connection exists, so no password has to be asked for
 */
public record WifiNetwork(String ssid, int signal, boolean secured, boolean active, boolean known) {

    /** Signal strength as 0-4 bars, the way the panel draws it. */
    public int bars() {
        return bars(signal);
    }

    public static int bars(int signal) {
        if (signal <= 0) return 0;
        if (signal < 25) return 1;
        if (signal < 50) return 2;
        if (signal < 75) return 3;
        return 4;
    }
}
