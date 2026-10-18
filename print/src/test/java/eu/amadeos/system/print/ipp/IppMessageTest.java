package eu.amadeos.system.print.ipp;

import eu.amadeos.system.print.ipp.IppMessage.Attribute;
import eu.amadeos.system.print.ipp.IppMessage.Group;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IppMessageTest {
    @Test
    void encodesTheWireFormat() {
        IppMessage request = new IppMessage(IppMessage.VERSION_2_0, 0x000A, 7, List.of(new Group(IppMessage.OPERATION,
                List.of(Attribute.of("attributes-charset", IppMessage.CHARSET, "utf-8"),
                        Attribute.of("requested-attributes", IppMessage.KEYWORD, "job-id", "job-name")))));
        byte[] expected = bytes(out -> {
            out.writeShort(0x0200);
            out.writeShort(0x000A);
            out.writeInt(7);
            out.writeByte(0x01);
            value(out, 0x47, "attributes-charset", "utf-8");
            value(out, 0x44, "requested-attributes", "job-id");
            value(out, 0x44, "", "job-name");
            out.writeByte(0x03);
        });
        assertArrayEquals(expected, request.encode());
    }

    @Test
    void decodesWhatItEncodes() throws IppException {
        IppMessage message = new IppMessage(IppMessage.VERSION_2_0, 0x0000, 3, List.of(
                new Group(IppMessage.OPERATION, List.of(Attribute.of("status-message", IppMessage.TEXT, "successful-ok"))),
                new Group(IppMessage.PRINTER, List.of(
                        Attribute.of("printer-name", IppMessage.NAME, "Büro"),
                        Attribute.of("printer-state", IppMessage.ENUM, 3),
                        Attribute.of("printer-is-accepting-jobs", IppMessage.BOOLEAN, true),
                        Attribute.of("printer-state-reasons", IppMessage.KEYWORD, "none", "paused"))),
                new Group(IppMessage.PRINTER, List.of(Attribute.of("printer-name", IppMessage.NAME, "Lab")))));
        IppMessage decoded = IppMessage.decode(message.encode());
        assertEquals(message, decoded);
        assertTrue(decoded.successful());
        assertEquals(2, decoded.groups(IppMessage.PRINTER).size());
        Group first = decoded.groups(IppMessage.PRINTER).getFirst();
        assertEquals("Büro", first.string("printer-name").orElseThrow());
        assertEquals(3, first.integer("printer-state").orElseThrow());
        assertTrue(first.bool("printer-is-accepting-jobs").orElseThrow());
        assertEquals(List.of("none", "paused"), first.strings("printer-state-reasons"));
    }

    @Test
    void decodesLanguageTaggedTextDatesAndMissingValues() throws IppException {
        byte[] data = bytes(out -> {
            out.writeShort(0x0200);
            out.writeShort(0x0406);
            out.writeInt(1);
            out.writeByte(0x02);
            ByteArrayOutputStream text = new ByteArrayOutputStream();
            DataOutputStream t = new DataOutputStream(text);
            t.writeShort(2);
            t.write("de".getBytes(StandardCharsets.US_ASCII));
            t.writeShort(6);
            t.write("Brief!".getBytes(StandardCharsets.US_ASCII));
            raw(out, 0x35, "job-name", text.toByteArray());
            raw(out, 0x31, "date-time-at-creation", new byte[]{0x07, (byte) 0xEA, 10, 17, 13, 5, 30, 0, '+', 2, 0});
            raw(out, 0x13, "job-k-octets", new byte[0]);
            out.writeByte(0x03);
        });
        IppMessage decoded = IppMessage.decode(data);
        assertFalse(decoded.successful());
        Group job = decoded.group(IppMessage.JOB).orElseThrow();
        assertEquals("Brief!", job.string("job-name").orElseThrow());
        assertEquals(ZonedDateTime.of(2026, 10, 17, 13, 5, 30, 0, ZoneOffset.ofHours(2)),
                job.value("date-time-at-creation").orElseThrow());
        assertTrue(job.integer("job-k-octets").isEmpty());
    }

    @Test
    void truncatedMessagesAreErrors() {
        assertThrows(IppException.class, () -> IppMessage.decode(new byte[]{2, 0, 0, 0, 0}));
    }

    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }

    private static byte[] bytes(Writer writer) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            writer.write(out);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return bytes.toByteArray();
    }

    private static void value(DataOutputStream out, int tag, String name, String value) throws IOException {
        raw(out, tag, name, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void raw(DataOutputStream out, int tag, String name, byte[] value) throws IOException {
        out.writeByte(tag);
        out.writeShort(name.length());
        out.write(name.getBytes(StandardCharsets.UTF_8));
        out.writeShort(value.length);
        out.write(value);
    }
}
