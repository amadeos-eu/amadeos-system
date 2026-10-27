package eu.amadeos.system.base.net;

import java.util.List;

/**
 * What {@code nmcli device show} tells about one device.
 *
 * @param hardwareAddress MAC address, e.g. {@code 3C:52:82:1A:0B:7E}, empty if unknown
 * @param ipv4            addresses with prefix length, e.g. {@code 192.168.1.23/24}
 * @param gateway         IPv4 gateway (router), empty without one
 * @param dns             IPv4 name servers
 * @param ipv6            IPv6 addresses with prefix length
 * @param speed           link speed as NetworkManager reports it, e.g. {@code 1000 Mb/s}, empty if unknown
 */
public record DeviceDetails(String hardwareAddress, List<String> ipv4, String gateway, List<String> dns,
                            List<String> ipv6, String speed) {

    public static final DeviceDetails NONE = new DeviceDetails("", List.of(), "", List.of(), List.of(), "");

    public DeviceDetails {
        ipv4 = List.copyOf(ipv4);
        dns = List.copyOf(dns);
        ipv6 = List.copyOf(ipv6);
    }

    /** The first IPv4 address without its prefix length, e.g. {@code 192.168.1.23}, or "". */
    public String address() {
        return ipv4.isEmpty() ? "" : ipv4.get(0).replaceFirst("/\\d+$", "");
    }
}
