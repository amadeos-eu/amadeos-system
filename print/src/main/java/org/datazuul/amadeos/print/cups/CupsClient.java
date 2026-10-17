package org.datazuul.amadeos.print.cups;

import org.datazuul.amadeos.print.ipp.IppException;
import org.datazuul.amadeos.print.ipp.IppMessage;
import org.datazuul.amadeos.print.ipp.IppMessage.Attribute;
import org.datazuul.amadeos.print.ipp.IppMessage.Group;
import org.datazuul.amadeos.print.ipp.IppTransport;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** The CUPS operations of the print manager, as IPP requests. Blocking: call them off the event thread. */
public final class CupsClient {
    static final int CANCEL_JOB = 0x0008;
    static final int GET_JOBS = 0x000A;
    static final int HOLD_JOB = 0x000C;
    static final int RELEASE_JOB = 0x000D;
    static final int PAUSE_PRINTER = 0x0010;
    static final int RESUME_PRINTER = 0x0011;
    static final int CUPS_GET_DEFAULT = 0x4001;
    static final int CUPS_GET_PRINTERS = 0x4002;
    static final int CUPS_SET_DEFAULT = 0x400A;

    /** Status client-error-not-found: e.g. no default printer. */
    static final int NOT_FOUND = 0x0406;

    private static final List<String> PRINTER_ATTRIBUTES = List.of("printer-name", "printer-info", "printer-location",
            "printer-make-and-model", "printer-state", "printer-state-message", "printer-state-reasons",
            "printer-is-accepting-jobs", "printer-uri-supported", "queued-job-count");
    private static final List<String> JOB_ATTRIBUTES = List.of("job-id", "job-name", "job-originating-user-name",
            "job-state", "job-printer-uri", "job-k-octets", "time-at-creation");

    private final IppTransport transport;
    private final String user;
    private final String language;
    private final AtomicInteger requestIds = new AtomicInteger();

    public CupsClient(IppTransport transport, String user, Locale locale) {
        this.transport = transport;
        this.user = user;
        this.language = locale.toLanguageTag().toLowerCase(Locale.ROOT);
    }

    /** All printers and classes, the default one marked, sorted by name. */
    public List<Printer> printers() throws IppException {
        IppMessage response = send("/", CUPS_GET_PRINTERS,
                Attribute.of("requested-attributes", IppMessage.KEYWORD, PRINTER_ATTRIBUTES.toArray()));
        String defaultName = defaultPrinter().orElse("");
        List<Printer> printers = new ArrayList<>();
        for (Group g : response.groups(IppMessage.PRINTER)) {
            String name = g.string("printer-name").orElse("");
            if (name.isEmpty()) continue;
            List<String> reasons = g.strings("printer-state-reasons").stream().filter(r -> !r.equals("none")).toList();
            printers.add(new Printer(name, g.string("printer-info").orElse(""), g.string("printer-location").orElse(""),
                    g.string("printer-make-and-model").orElse(""), Printer.State.of(g.integer("printer-state").orElse(0)),
                    g.string("printer-state-message").orElse(""), reasons,
                    g.bool("printer-is-accepting-jobs").orElse(true),
                    g.string("printer-uri-supported").orElse(printerUri(name)), g.integer("queued-job-count").orElse(0),
                    name.equals(defaultName)));
        }
        printers.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return printers;
    }

    /** The name of the default printer, empty if there is none. */
    public Optional<String> defaultPrinter() throws IppException {
        try {
            IppMessage response = send("/", CUPS_GET_DEFAULT,
                    Attribute.of("requested-attributes", IppMessage.KEYWORD, "printer-name"));
            return response.group(IppMessage.PRINTER).flatMap(g -> g.string("printer-name"));
        } catch (IppException e) {
            if (e.status() == NOT_FOUND) return Optional.empty();
            throw e;
        }
    }

    /** The jobs of all printers: those not finished yet, or with {@code history} all jobs CUPS still keeps. */
    public List<Job> jobs(boolean history) throws IppException {
        IppMessage response = send("/", GET_JOBS,
                Attribute.of("printer-uri", IppMessage.URI, "ipp://localhost/"),
                Attribute.of("which-jobs", IppMessage.KEYWORD, history ? "all" : "not-completed"),
                Attribute.of("requested-attributes", IppMessage.KEYWORD, JOB_ATTRIBUTES.toArray()));
        List<Job> jobs = new ArrayList<>();
        for (Group g : response.groups(IppMessage.JOB)) {
            Optional<Integer> id = g.integer("job-id");
            if (id.isEmpty()) continue;
            jobs.add(new Job(id.get(), g.string("job-name").orElse(""), g.string("job-originating-user-name").orElse(""),
                    g.string("job-printer-uri").map(CupsClient::queueName).orElse(""),
                    Job.State.of(g.integer("job-state").orElse(0)), g.integer("job-k-octets").orElse(0),
                    g.integer("time-at-creation").filter(t -> t > 0).map(t -> Instant.ofEpochSecond(t))));
        }
        // Newest first, as in a queue list where the active jobs are on top.
        jobs.sort((a, b) -> Integer.compare(b.id(), a.id()));
        return jobs;
    }

    public void cancel(int jobId) throws IppException {
        send("/jobs/", CANCEL_JOB, jobUri(jobId));
    }

    public void hold(int jobId) throws IppException {
        send("/jobs/", HOLD_JOB, jobUri(jobId));
    }

    public void release(int jobId) throws IppException {
        send("/jobs/", RELEASE_JOB, jobUri(jobId));
    }

    public void pause(Printer printer) throws IppException {
        send("/admin/", PAUSE_PRINTER, Attribute.of("printer-uri", IppMessage.URI, printer.uri()));
    }

    public void resume(Printer printer) throws IppException {
        send("/admin/", RESUME_PRINTER, Attribute.of("printer-uri", IppMessage.URI, printer.uri()));
    }

    public void makeDefault(Printer printer) throws IppException {
        send("/admin/", CUPS_SET_DEFAULT, Attribute.of("printer-uri", IppMessage.URI, printer.uri()));
    }

    private Attribute jobUri(int jobId) {
        return Attribute.of("job-uri", IppMessage.URI, "ipp://localhost/jobs/" + jobId);
    }

    private static String printerUri(String name) {
        return "ipp://localhost/printers/" + name;
    }

    /** The last path segment of a printer URI: {@code ipp://localhost/printers/HP} gives {@code HP}. */
    static String queueName(String uri) {
        String path = URI.create(uri.replace(" ", "%20")).getPath();
        if (path == null) return uri;
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private IppMessage send(String resource, int operation, Attribute... attributes) throws IppException {
        List<Attribute> group = new ArrayList<>();
        group.add(Attribute.of("attributes-charset", IppMessage.CHARSET, "utf-8"));
        group.add(Attribute.of("attributes-natural-language", IppMessage.NATURAL_LANGUAGE, language));
        // Target attributes (printer-uri, job-uri) must come right after the charset and language.
        group.addAll(List.of(attributes));
        group.add(Attribute.of("requesting-user-name", IppMessage.NAME, user));
        IppMessage request = new IppMessage(IppMessage.VERSION_2_0, operation, requestIds.incrementAndGet(),
                List.of(new Group(IppMessage.OPERATION, group)));
        IppMessage response = IppMessage.decode(transport.post(resource, request.encode()));
        if (!response.successful()) {
            String message = response.group(IppMessage.OPERATION).flatMap(g -> g.string("status-message"))
                    .orElse("IPP status 0x" + Integer.toHexString(response.code()));
            throw new IppException(message, response.code());
        }
        return response;
    }
}
