package eu.amadeos.system.printers;

import eu.amadeos.system.print.cups.Printer;

import java.util.List;

/**
 * Printer administration as the Settings do it. Calls block (finding printers takes seconds), so callers run them
 * off the event dispatch thread. Users in the group {@code lpadmin} may do all of it without a password.
 */
public interface PrinterService {

    /** The printers CUPS has, the default one marked; empty when CUPS does not answer. */
    List<Printer> printers() throws PrinterException;

    /** Printers that can be added: found in the network or on USB, and able to print without a driver. */
    List<FoundPrinter> discover() throws PrinterException;

    /**
     * Sets a printer up as a driverless queue (IPP Everywhere): CUPS asks the printer what it can and makes its
     * description itself.
     */
    void add(String name, String uri, String info, String location) throws PrinterException;

    void remove(Printer printer) throws PrinterException;

    void makeDefault(Printer printer) throws PrinterException;

    /** Prints CUPS's test page on the printer. */
    void printTestPage(Printer printer) throws PrinterException;

    PrinterOptions options(Printer printer);

    void setOptions(Printer printer, String pageSize, String duplex) throws PrinterException;

    /** Something CUPS refused, with its reason. */
    final class PrinterException extends Exception {
        public PrinterException(String message) {
            super(message);
        }
    }
}
