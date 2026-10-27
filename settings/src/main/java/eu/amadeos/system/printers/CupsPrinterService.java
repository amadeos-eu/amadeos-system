package eu.amadeos.system.printers;

import eu.amadeos.system.print.cups.CupsClient;
import eu.amadeos.system.print.cups.Printer;
import eu.amadeos.system.print.ipp.IppException;
import eu.amadeos.system.print.ipp.IppTransport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link PrinterService} on the local CUPS: the printer list through the print manager's IPP client, changes
 * through {@code lpadmin}, {@code lpinfo}, {@code lpoptions} and {@code lp}, which talk to CUPS over its socket
 * as the user (a member of {@code lpadmin} on amadeOS).
 */
public final class CupsPrinterService implements PrinterService {
    private static final Logger LOGGER = Logger.getLogger(CupsPrinterService.class.getName());
    private static final long TIMEOUT_SECONDS = 60;

    private final CupsClient cups;
    private final String testPageTitle;

    public CupsPrinterService(String user, Locale locale, String testPageTitle) {
        this.cups = new CupsClient(IppTransport.local(user), user, locale);
        this.testPageTitle = testPageTitle;
    }

    @Override
    public List<Printer> printers() throws PrinterException {
        try {
            return cups.printers();
        } catch (IppException e) {
            throw new PrinterException(e.getMessage());
        }
    }

    @Override
    public List<FoundPrinter> discover() throws PrinterException {
        return Lpadmin.parseDevices(run(Lpadmin.discover()));
    }

    @Override
    public void add(String name, String uri, String info, String location) throws PrinterException {
        run(Lpadmin.add(name, uri, info, location));
    }

    @Override
    public void remove(Printer printer) throws PrinterException {
        run(Lpadmin.remove(printer.name()));
    }

    @Override
    public void makeDefault(Printer printer) throws PrinterException {
        run(Lpadmin.makeDefault(printer.name()));
    }

    @Override
    public void printTestPage(Printer printer) throws PrinterException {
        run(Lpadmin.testPage(printer.name(), testPageTitle));
    }

    @Override
    public PrinterOptions options(Printer printer) {
        try {
            return Lpadmin.parseOptions(run(Lpadmin.options(printer.name())));
        } catch (PrinterException e) {
            LOGGER.log(Level.FINE, "No options for " + printer.name(), e);
            return PrinterOptions.NONE;
        }
    }

    @Override
    public void setOptions(Printer printer, String pageSize, String duplex) throws PrinterException {
        run(Lpadmin.setOptions(printer.name(), pageSize, duplex));
    }

    /** Runs a CUPS tool and returns its output; a non-zero exit becomes a PrinterException with its message. */
    private static String run(List<String> command) throws PrinterException {
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.environment().put("LC_ALL", "C.UTF-8");
            Process process = builder.start();
            process.getOutputStream().close();
            CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()));
            String stdout = read(process.getInputStream());
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new PrinterException(command.get(0) + ": timeout");
            }
            if (process.exitValue() != 0) {
                String message = stderr.join().strip();
                throw new PrinterException(message.isEmpty() ? command.get(0) + " failed" : message.lines().reduce((a, b) -> b).orElse(message));
            }
            return stdout;
        } catch (IOException e) {
            throw new PrinterException(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PrinterException("interrupted");
        }
    }

    private static String read(InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
