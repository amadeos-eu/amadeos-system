package eu.amadeos.print.cups;

import eu.amadeos.print.ipp.IppException;
import eu.amadeos.print.ipp.IppMessage;
import eu.amadeos.print.ipp.IppMessage.Attribute;
import eu.amadeos.print.ipp.IppMessage.Group;
import eu.amadeos.print.ipp.IppTransport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CupsClientTest {
    /** Answers with prepared responses and keeps the decoded requests. */
    private static final class FakeCups implements IppTransport {
        final Deque<IppMessage> responses = new ArrayDeque<>();
        final List<IppMessage> requests = new ArrayList<>();
        final List<String> resources = new ArrayList<>();

        @Override
        public byte[] post(String resource, byte[] request) throws IppException {
            requests.add(IppMessage.decode(request));
            resources.add(resource);
            return responses.removeFirst().encode();
        }

        FakeCups answer(int status, Group... groups) {
            List<Group> all = new ArrayList<>();
            all.add(new Group(IppMessage.OPERATION, List.of(
                    Attribute.of("attributes-charset", IppMessage.CHARSET, "utf-8"),
                    Attribute.of("status-message", IppMessage.TEXT, status == 0 ? "successful-ok" : "Forbidden"))));
            all.addAll(List.of(groups));
            responses.add(new IppMessage(IppMessage.VERSION_2_0, status, 1, all));
            return this;
        }
    }

    private final FakeCups cups = new FakeCups();
    private final CupsClient client = new CupsClient(cups, "alice", Locale.GERMANY);

    private static Group printer(String name, int state, String... more) {
        List<Attribute> attributes = new ArrayList<>(List.of(
                Attribute.of("printer-name", IppMessage.NAME, name),
                Attribute.of("printer-state", IppMessage.ENUM, state),
                Attribute.of("printer-state-reasons", IppMessage.KEYWORD, (Object[]) (more.length > 0 ? more : new String[]{"none"})),
                Attribute.of("printer-uri-supported", IppMessage.URI, "ipp://localhost/printers/" + name),
                Attribute.of("queued-job-count", IppMessage.INTEGER, 2)));
        return new Group(IppMessage.PRINTER, attributes);
    }

    @Test
    void listsThePrintersWithTheDefaultMarked() throws IppException {
        cups.answer(0, printer("Lab", 5, "paused"), printer("Buero", 3))
                .answer(0, new Group(IppMessage.PRINTER, List.of(Attribute.of("printer-name", IppMessage.NAME, "Buero"))));

        List<Printer> printers = client.printers();

        assertEquals(List.of("Buero", "Lab"), printers.stream().map(Printer::name).toList());
        Printer buero = printers.getFirst();
        assertTrue(buero.isDefault());
        assertEquals(Printer.State.IDLE, buero.state());
        assertEquals(List.of(), buero.reasons());
        assertEquals(2, buero.jobCount());
        Printer lab = printers.get(1);
        assertFalse(lab.isDefault());
        assertEquals(Printer.State.STOPPED, lab.state());
        assertEquals(List.of("paused"), lab.reasons());

        IppMessage request = cups.requests.getFirst();
        assertEquals(0x4002, request.code());
        Group operation = request.group(IppMessage.OPERATION).orElseThrow();
        assertEquals(List.of("attributes-charset", "attributes-natural-language", "requested-attributes",
                "requesting-user-name"), operation.attributes().stream().map(Attribute::name).toList());
        assertEquals("de-de", operation.string("attributes-natural-language").orElseThrow());
        assertEquals("alice", operation.string("requesting-user-name").orElseThrow());
    }

    @Test
    void noDefaultPrinterIsNotAnError() throws IppException {
        cups.answer(0x0406);
        assertEquals(Optional.empty(), client.defaultPrinter());
    }

    @Test
    void listsTheJobsNewestFirst() throws IppException {
        cups.answer(0,
                new Group(IppMessage.JOB, List.of(
                        Attribute.of("job-id", IppMessage.INTEGER, 41),
                        Attribute.of("job-name", IppMessage.NAME, "Brief.pdf"),
                        Attribute.of("job-originating-user-name", IppMessage.NAME, "alice"),
                        Attribute.of("job-state", IppMessage.ENUM, 4),
                        Attribute.of("job-printer-uri", IppMessage.URI, "ipp://localhost/printers/Buero"),
                        Attribute.of("job-k-octets", IppMessage.INTEGER, 120),
                        Attribute.of("time-at-creation", IppMessage.INTEGER, 1_700_000_000))),
                new Group(IppMessage.JOB, List.of(
                        Attribute.of("job-id", IppMessage.INTEGER, 42),
                        Attribute.of("job-state", IppMessage.ENUM, 5),
                        Attribute.of("job-printer-uri", IppMessage.URI, "ipp://localhost/classes/All"))));

        List<Job> jobs = client.jobs(false);

        assertEquals(List.of(42, 41), jobs.stream().map(Job::id).toList());
        Job letter = jobs.get(1);
        assertEquals(new Job(41, "Brief.pdf", "alice", "Buero", Job.State.HELD, 120,
                Optional.of(Instant.ofEpochSecond(1_700_000_000))), letter);
        assertTrue(letter.releasable());
        assertFalse(letter.holdable());
        assertEquals("All", jobs.getFirst().printer());
        Group operation = cups.requests.getFirst().group(IppMessage.OPERATION).orElseThrow();
        assertEquals("ipp://localhost/", operation.string("printer-uri").orElseThrow());
        assertEquals("not-completed", operation.string("which-jobs").orElseThrow());
    }

    @Test
    void cancelNamesTheJobByItsUri() throws IppException {
        cups.answer(0);
        client.cancel(42);
        IppMessage request = cups.requests.getFirst();
        assertEquals(0x0008, request.code());
        assertEquals("/jobs/", cups.resources.getFirst());
        assertEquals("ipp://localhost/jobs/42",
                request.group(IppMessage.OPERATION).orElseThrow().string("job-uri").orElseThrow());
    }

    @Test
    void refusalsCarryTheStatus() {
        cups.answer(0x0403);
        Printer lab = new Printer("Lab", "", "", "", Printer.State.IDLE, "", List.of(), true,
                "ipp://localhost/printers/Lab", 0, false);
        IppException e = assertThrows(IppException.class, () -> client.pause(lab));
        assertTrue(e.notAllowed());
        assertEquals("Forbidden", e.getMessage());
        assertEquals(0x0010, cups.requests.getFirst().code());
        assertEquals("/admin/", cups.resources.getFirst());
    }

    @Test
    void queueNamesComeFromTheUri() {
        assertEquals("HP_LaserJet", CupsClient.queueName("ipp://localhost/printers/HP_LaserJet"));
        assertEquals("Office", CupsClient.queueName("ipp://localhost:631/classes/Office"));
    }
}
