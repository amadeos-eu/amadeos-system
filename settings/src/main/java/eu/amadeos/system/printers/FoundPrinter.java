package eu.amadeos.system.printers;

/**
 * A printer CUPS found in the network or on USB (through ipp-usb) that can print without a driver.
 *
 * @param uri          where CUPS reaches it, e.g. {@code dnssd://Brother%20HL-L2350DW._ipp._tcp.local/}
 * @param info         its name as it announces itself, e.g. "Brother HL-L2350DW series"
 * @param makeAndModel e.g. "Brother HL-L2350DW series"
 * @param location     where it stands, if it says so
 */
public record FoundPrinter(String uri, String info, String makeAndModel, String location) {

    /** A queue name for it: letters, digits, '-' and '_' only, as lpadmin wants, e.g. {@code Brother_HL-L2350DW_series}. */
    public String queueName() {
        return queueName(info.isBlank() ? makeAndModel : info);
    }

    public static String queueName(String text) {
        String name = text.strip().replaceAll("[^A-Za-z0-9_-]+", "_").replaceAll("^_+|_+$", "");
        if (name.isEmpty()) name = "Printer";
        return name.length() > 127 ? name.substring(0, 127) : name;
    }
}
