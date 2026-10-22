package eu.amadeos.system.sysinfo.service;

import eu.amadeos.commons.process.CommandRunner;
import eu.amadeos.commons.process.ProgramMissingException;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/** The sources of this computer: commands on the PATH, the JDK's network interfaces and screens. */
public final class LocalSources implements SystemProbe.Sources {
    private static final Logger LOG = LoggerFactory.getLogger(LocalSources.class);
    /** {@code lspci} answers in a fraction of a second; a hanging one must not keep the page empty. */
    static final Duration TIME_LIMIT = Duration.ofSeconds(5);

    private final CommandRunner runner;

    /** @param runner starts the commands; {@link CommandRunner#system()} on a real computer */
    public LocalSources(CommandRunner runner) {
        this.runner = runner;
    }

    // --8<-- [start:run]
    @Override
    public Optional<String> run(List<String> command) {
        try {
            return Optional.of(runner.output(command, TIME_LIMIT));
        } catch (ProgramMissingException e) {
            LOG.debug("{} is not installed", e.program());
        } catch (IOException e) {
            LOG.debug("{} gave no answer: {}", command.getFirst(), e.getMessage());
        }
        return Optional.empty();
    }
    // --8<-- [end:run]

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
