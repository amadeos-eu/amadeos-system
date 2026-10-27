package eu.amadeos.system.printers;

import java.util.List;

/**
 * The defaults of a printer the Printers page offers: paper size and two-sided printing, from the PPD CUPS made
 * for it ({@code lpoptions -p NAME -l}).
 *
 * @param pageSizes  the paper sizes the printer knows, e.g. A4, Letter
 * @param pageSize   the default one, empty if the printer has no choice
 * @param duplexes   {@code None}, {@code DuplexNoTumble} (long edge), {@code DuplexTumble} (short edge); empty for a
 *                   printer that cannot print on both sides
 * @param duplex     the default one
 */
public record PrinterOptions(List<String> pageSizes, String pageSize, List<String> duplexes, String duplex) {

    public static final PrinterOptions NONE = new PrinterOptions(List.of(), "", List.of(), "");

    public PrinterOptions {
        pageSizes = List.copyOf(pageSizes);
        duplexes = List.copyOf(duplexes);
    }

    public boolean canDuplex() {
        return duplexes.size() > 1;
    }
}
