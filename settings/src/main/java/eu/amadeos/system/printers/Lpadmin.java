package eu.amadeos.system.printers;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The command lines of CUPS's administration tools the Printers page uses, and parsers for their output. Pure, so
 * it can be tested without CUPS. The tools run with {@code LC_ALL=C}.
 */
final class Lpadmin {
    /** CUPS's own test page, which every printer can print. */
    static final String TEST_PAGE = "/usr/share/cups/data/testprint";
    private static final Pattern OPTION = Pattern.compile("([^/:]+)(?:/[^:]*)?:\\s*(.*)");

    private Lpadmin() {
    }

    // --8<-- [start:commands]
    /** Driverless printers only: announced with DNS-SD in the network, or USB printers through ipp-usb. */
    static List<String> discover() {
        return List.of("lpinfo", "--include-schemes", "dnssd", "-l", "-v");
    }

    static List<String> add(String name, String uri, String info, String location) {
        List<String> args = new ArrayList<>(List.of("lpadmin", "-p", name, "-E", "-v", uri, "-m", "everywhere"));
        if (!info.isBlank()) args.addAll(List.of("-D", info));
        if (!location.isBlank()) args.addAll(List.of("-L", location));
        return List.copyOf(args);
    }
    // --8<-- [end:commands]

    static List<String> remove(String name) {
        return List.of("lpadmin", "-x", name);
    }

    static List<String> makeDefault(String name) {
        return List.of("lpadmin", "-d", name);
    }

    static List<String> testPage(String name, String title) {
        return List.of("lp", "-d", name, "-t", title, TEST_PAGE);
    }

    static List<String> options(String name) {
        return List.of("lpoptions", "-p", name, "-l");
    }

    static List<String> setOptions(String name, String pageSize, String duplex) {
        List<String> args = new ArrayList<>(List.of("lpadmin", "-p", name));
        if (!pageSize.isEmpty()) args.addAll(List.of("-o", "PageSize-default=" + pageSize));
        if (!duplex.isEmpty()) args.addAll(List.of("-o", "Duplex-default=" + duplex));
        return List.copyOf(args);
    }

    /**
     * The devices of {@code lpinfo -l -v}: blocks starting with {@code Device: uri = ...} followed by indented
     * {@code key = value} lines.
     */
    static List<FoundPrinter> parseDevices(String output) {
        List<FoundPrinter> found = new ArrayList<>();
        String uri = null;
        String info = "";
        String model = "";
        String location = "";
        for (String line : (output + "\nDevice: uri = ").split("\n")) {
            String l = line.strip();
            if (l.startsWith("Device:")) {
                if (uri != null && !uri.isEmpty()) found.add(new FoundPrinter(uri, info, model, location));
                uri = value(l.substring("Device:".length()));
                info = "";
                model = "";
                location = "";
            } else if (l.startsWith("info =")) {
                info = value(l);
            } else if (l.startsWith("make-and-model =")) {
                model = value(l);
            } else if (l.startsWith("location =")) {
                location = value(l);
            }
        }
        return found;
    }

    /** {@code PageSize/Media Size: Letter *A4 Legal} and {@code Duplex/2-Sided Printing: *None DuplexNoTumble ...}. */
    static PrinterOptions parseOptions(String output) {
        List<String> sizes = List.of();
        String size = "";
        List<String> duplexes = List.of();
        String duplex = "";
        for (String line : output.split("\n")) {
            Matcher m = OPTION.matcher(line.strip());
            if (!m.matches()) continue;
            List<String> values = new ArrayList<>();
            String chosen = "";
            for (String v : m.group(2).strip().split("\\s+")) {
                if (v.isEmpty()) continue;
                if (v.startsWith("*")) {
                    v = v.substring(1);
                    chosen = v;
                }
                values.add(v);
            }
            if (m.group(1).equals("PageSize")) {
                sizes = values;
                size = chosen;
            } else if (m.group(1).equals("Duplex")) {
                duplexes = values;
                duplex = chosen;
            }
        }
        return new PrinterOptions(sizes, size, duplexes, duplex);
    }

    private static String value(String keyValue) {
        int eq = keyValue.indexOf('=');
        return eq < 0 ? "" : keyValue.substring(eq + 1).strip();
    }
}
