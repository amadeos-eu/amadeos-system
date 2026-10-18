package eu.amadeos.print.ipp;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An IPP request or response in the binary encoding of RFC 8010: version, operation id (request) or status
 * code (response), request id and attribute groups. Only what a print manager needs is decoded as values:
 * integers, enums and booleans as {@link Integer} / {@link Boolean}, all strings as {@link String}, dateTime as
 * {@link ZonedDateTime}; other values (resolution, range, collections) as their raw bytes.
 */
public record IppMessage(int version, int code, int requestId, List<Group> groups) {
    public static final int VERSION_2_0 = 0x0200;

    // Delimiter tags
    public static final int OPERATION = 0x01;
    public static final int JOB = 0x02;
    public static final int END = 0x03;
    public static final int PRINTER = 0x04;
    public static final int UNSUPPORTED = 0x05;

    // Value tags
    public static final int NO_VALUE = 0x13;
    public static final int INTEGER = 0x21;
    public static final int BOOLEAN = 0x22;
    public static final int ENUM = 0x23;
    public static final int OCTET_STRING = 0x30;
    public static final int DATE_TIME = 0x31;
    public static final int TEXT_WITH_LANGUAGE = 0x35;
    public static final int NAME_WITH_LANGUAGE = 0x36;
    public static final int TEXT = 0x41;
    public static final int NAME = 0x42;
    public static final int KEYWORD = 0x44;
    public static final int URI = 0x45;
    public static final int CHARSET = 0x47;
    public static final int NATURAL_LANGUAGE = 0x48;
    public static final int MIME_MEDIA_TYPE = 0x49;

    public IppMessage {
        groups = List.copyOf(groups);
    }

    /** An attribute with one or more values ({@code 1setOf}). */
    public record Attribute(String name, int tag, List<Object> values) {
        public Attribute {
            values = List.copyOf(values);
        }

        public static Attribute of(String name, int tag, Object... values) {
            return new Attribute(name, tag, List.of(values));
        }
    }

    /** The attributes after a delimiter tag: operation, job or printer attributes. */
    public record Group(int tag, List<Attribute> attributes) {
        public Group {
            attributes = List.copyOf(attributes);
        }

        public Optional<Attribute> attribute(String name) {
            return attributes.stream().filter(a -> a.name().equals(name)).findFirst();
        }

        public Optional<Object> value(String name) {
            return attribute(name).filter(a -> !a.values().isEmpty()).map(a -> a.values().getFirst());
        }

        public Optional<String> string(String name) {
            return value(name).filter(String.class::isInstance).map(String.class::cast);
        }

        public Optional<Integer> integer(String name) {
            return value(name).filter(Integer.class::isInstance).map(Integer.class::cast);
        }

        public Optional<Boolean> bool(String name) {
            return value(name).filter(Boolean.class::isInstance).map(Boolean.class::cast);
        }

        /** All string values, e.g. of {@code printer-state-reasons}. */
        public List<String> strings(String name) {
            return attribute(name).map(a -> a.values().stream().filter(String.class::isInstance)
                    .map(String.class::cast).toList()).orElse(List.of());
        }
    }

    /** The groups with this delimiter tag, e.g. one {@link #JOB} group per job. */
    public List<Group> groups(int tag) {
        return groups.stream().filter(g -> g.tag() == tag).toList();
    }

    public Optional<Group> group(int tag) {
        return groups.stream().filter(g -> g.tag() == tag).findFirst();
    }

    /** Status codes 0x0000 to 0x00FF are successful. */
    public boolean successful() {
        return code >= 0 && code <= 0xFF;
    }

    public byte[] encode() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeShort(version);
            out.writeShort(code);
            out.writeInt(requestId);
            for (Group group : groups) {
                out.writeByte(group.tag());
                for (Attribute attribute : group.attributes()) {
                    List<Object> values = attribute.values().isEmpty() ? java.util.Collections.singletonList(null)
                            : attribute.values();
                    for (int i = 0; i < values.size(); i++) {
                        // Further values of a 1setOf repeat the tag with an empty name.
                        out.writeByte(attribute.values().isEmpty() ? NO_VALUE : attribute.tag());
                        byte[] name = i == 0 ? attribute.name().getBytes(StandardCharsets.UTF_8) : new byte[0];
                        out.writeShort(name.length);
                        out.write(name);
                        byte[] value = encodeValue(attribute.tag(), values.get(i));
                        out.writeShort(value.length);
                        out.write(value);
                    }
                }
            }
            out.writeByte(END);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static byte[] encodeValue(int tag, Object value) {
        return switch (value) {
            case null -> new byte[0];
            case Integer i -> ByteBuffer.allocate(4).putInt(i).array();
            case Boolean b -> new byte[]{(byte) (b ? 1 : 0)};
            case String s -> s.getBytes(StandardCharsets.UTF_8);
            case byte[] raw -> raw;
            default -> throw new IllegalArgumentException("Cannot encode " + value.getClass() + " as tag " + tag);
        };
    }

    /** Decodes a message; the document data that may follow the attributes is ignored. */
    public static IppMessage decode(byte[] data) throws IppException {
        try {
            ByteBuffer in = ByteBuffer.wrap(data);
            int version = in.getShort() & 0xFFFF;
            int code = in.getShort() & 0xFFFF;
            int requestId = in.getInt();
            List<Group> groups = new ArrayList<>();
            int groupTag = -1;
            List<Attribute> attributes = new ArrayList<>();
            while (true) {
                int tag = in.get() & 0xFF;
                if (tag <= 0x0F) {
                    if (groupTag >= 0) groups.add(new Group(groupTag, attributes));
                    if (tag == END) break;
                    groupTag = tag;
                    attributes = new ArrayList<>();
                    continue;
                }
                if (tag == 0x7F) throw new IppException("Extended value tags are not supported");
                String name = string(in, in.getShort() & 0xFFFF);
                byte[] raw = new byte[in.getShort() & 0xFFFF];
                in.get(raw);
                Object value = decodeValue(tag, raw);
                if (groupTag < 0) throw new IppException("Attribute " + name + " outside of a group");
                if (name.isEmpty() && !attributes.isEmpty()) {
                    // Additional value of the previous attribute; member attributes of collections end up here too.
                    Attribute previous = attributes.removeLast();
                    List<Object> values = new ArrayList<>(previous.values());
                    if (value != null) values.add(value);
                    attributes.add(new Attribute(previous.name(), previous.tag(), values));
                } else {
                    attributes.add(new Attribute(name, tag, value == null ? List.of() : List.of(value)));
                }
            }
            return new IppMessage(version, code, requestId, groups);
        } catch (BufferUnderflowException e) {
            throw new IppException("Truncated IPP message");
        }
    }

    private static Object decodeValue(int tag, byte[] raw) {
        if (tag >= 0x10 && tag <= 0x1F) return null; // out-of-band: unsupported, unknown, no-value
        return switch (tag) {
            case INTEGER, ENUM -> raw.length == 4 ? ByteBuffer.wrap(raw).getInt() : null;
            case BOOLEAN -> raw.length == 1 ? raw[0] != 0 : null;
            case DATE_TIME -> dateTime(raw);
            case TEXT_WITH_LANGUAGE, NAME_WITH_LANGUAGE -> withLanguage(raw);
            default -> tag >= 0x40 && tag <= 0x5F || tag == OCTET_STRING
                    ? new String(raw, StandardCharsets.UTF_8) : raw;
        };
    }

    /** RFC 2579 DateAndTime: year, month, day, hour, minutes, seconds, deci-seconds, UTC direction, hours, minutes. */
    private static ZonedDateTime dateTime(byte[] raw) {
        if (raw.length != 11) return null;
        ByteBuffer b = ByteBuffer.wrap(raw);
        int year = b.getShort() & 0xFFFF;
        int month = b.get(), day = b.get(), hour = b.get(), minute = b.get(), second = b.get();
        int deci = b.get();
        int sign = b.get() == '-' ? -1 : 1;
        int offsetHours = b.get(), offsetMinutes = b.get();
        try {
            return ZonedDateTime.of(year, month, day, hour, minute, second, deci * 100_000_000,
                    ZoneOffset.ofHoursMinutes(sign * offsetHours, sign * offsetMinutes));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** textWithLanguage / nameWithLanguage: the language, then the text, each with a two-byte length. */
    private static String withLanguage(byte[] raw) {
        ByteBuffer b = ByteBuffer.wrap(raw);
        try {
            b.position(2 + (b.getShort() & 0xFFFF));
            return string(b, b.getShort() & 0xFFFF);
        } catch (RuntimeException e) {
            return new String(raw, StandardCharsets.UTF_8);
        }
    }

    private static String string(ByteBuffer in, int length) {
        byte[] bytes = new byte[length];
        in.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
