package eu.amadeos.system.packages;

/**
 * Parser for the standard output of {@code amadeos-packages}: apt's status lines ({@code APT::Status-Fd}) and
 * the helper's own "@@" lines. Pure, so it can be tested without apt.
 */
public final class HelperOutput {

    public sealed interface Line permits Status, Failure, Error, RebootRequired, Nothing, Done, Text {
    }

    /** {@code dlstatus:ITEM:PERCENT:MESSAGE} or {@code pmstatus:PACKAGE:PERCENT:MESSAGE}. */
    public record Status(Progress progress) implements Line {
    }

    /** {@code pmerror:PACKAGE:PERCENT:MESSAGE}: dpkg failed on one package; apt goes on and fails at the end. */
    public record Failure(String item, String message) implements Line {
    }

    /** {@code @@error ID MESSAGE}, ID one of usage, root, locked, apt. */
    public record Error(String id, String message) implements Line {
    }

    public record RebootRequired() implements Line {
    }

    /** {@code @@nothing}: an upgrade of security updates only found none. */
    public record Nothing() implements Line {
    }

    public record Done() implements Line {
    }

    /** Anything else, e.g. the "Inst" lines of {@code check}. */
    public record Text(String text) implements Line {
    }

    private HelperOutput() {
    }

    // --8<-- [start:parse]
    public static Line parse(String line) {
        if (line.startsWith("@@")) {
            String body = line.substring(2);
            int space = body.indexOf(' ');
            String word = space < 0 ? body : body.substring(0, space);
            String rest = space < 0 ? "" : body.substring(space + 1);
            switch (word) {
                case "done" -> {
                    return new Done();
                }
                case "nothing" -> {
                    return new Nothing();
                }
                case "reboot-required" -> {
                    return new RebootRequired();
                }
                case "error" -> {
                    int idEnd = rest.indexOf(' ');
                    return idEnd < 0 ? new Error(rest, "") : new Error(rest.substring(0, idEnd), rest.substring(idEnd + 1));
                }
                default -> {
                    return new Text(line);
                }
            }
        }
        // TYPE:ITEM:PERCENT:MESSAGE; the message may contain colons.
        String[] f = line.split(":", 4);
        if (f.length == 4) {
            switch (f[0]) {
                case "dlstatus" -> {
                    return new Status(new Progress(Progress.Phase.DOWNLOAD, percent(f[2]), f[1], f[3]));
                }
                case "pmstatus" -> {
                    return new Status(new Progress(Progress.Phase.INSTALL, percent(f[2]), f[1], f[3]));
                }
                case "pmerror" -> {
                    return new Failure(f[1], f[3]);
                }
                default -> {
                }
            }
        }
        return new Text(line);
    }
    // --8<-- [end:parse]

    private static int percent(String value) {
        try {
            return (int) Math.max(0, Math.min(100, Math.round(Double.parseDouble(value))));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
