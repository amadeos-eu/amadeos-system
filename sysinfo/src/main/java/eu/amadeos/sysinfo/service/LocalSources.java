package eu.amadeos.sysinfo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.DisplayMode;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** The sources of this computer: commands on the PATH, the JDK's network interfaces and screens. */
public final class LocalSources implements SystemProbe.Sources {
    private static final Logger LOG = LoggerFactory.getLogger(LocalSources.class);

    @Override
    public Optional<String> run(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            byte[] output = process.getInputStream().readAllBytes();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            return process.exitValue() == 0 ? Optional.of(new String(output, StandardCharsets.UTF_8)) : Optional.empty();
        } catch (IOException e) {
            LOG.debug("{} is not available: {}", command.getFirst(), e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    @Override
    public List<SystemProbe.Interface> interfaces() {
        List<SystemProbe.Interface> list = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (ni.isLoopback() || ni.isVirtual()) continue;
                byte[] hardware = ni.getHardwareAddress();
                String mac = hardware == null || hardware.length == 0 ? ""
                        : HexFormat.ofDelimiter(":").formatHex(hardware);
                List<String> addresses = new ArrayList<>();
                // IPv4 first, then IPv6, each with its prefix length.
                for (boolean v4 : new boolean[]{true, false}) {
                    for (InterfaceAddress a : ni.getInterfaceAddresses()) {
                        if ((a.getAddress() instanceof Inet4Address) != v4) continue;
                        String host = a.getAddress().getHostAddress();
                        int scope = host.indexOf('%');
                        addresses.add((scope > 0 ? host.substring(0, scope) : host) + "/" + a.getNetworkPrefixLength());
                    }
                }
                list.add(new SystemProbe.Interface(ni.getName(), mac, addresses, ni.isUp()));
            }
        } catch (SocketException e) {
            LOG.warn("Cannot list the network interfaces: {}", e.getMessage());
        }
        list.sort((a, b) -> a.name().compareTo(b.name()));
        return list;
    }

    @Override
    public List<String> screens() {
        if (GraphicsEnvironment.isHeadless()) return List.of();
        List<String> screens = new ArrayList<>();
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            DisplayMode mode = device.getDisplayMode();
            String size = mode.getWidth() + " × " + mode.getHeight();
            if (mode.getRefreshRate() != DisplayMode.REFRESH_RATE_UNKNOWN) size += ", " + mode.getRefreshRate() + " Hz";
            if (mode.getBitDepth() > 0) size += ", " + mode.getBitDepth() + " bit";
            screens.add(size);
        }
        return screens;
    }
}
