package eu.amadeos.system.polkit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One run of polkit's authentication helper, which checks the password with PAM as root and tells polkitd
 * that the cookie is authenticated. Since polkit 126 the helper can be reached through the socket
 * {@code /run/polkit/agent-helper.socket} (the user name goes first); otherwise the setuid program
 * {@code polkit-agent-helper-1 USER} is started. Then the cookie goes in, and the helper answers with lines:
 * {@code PAM_PROMPT_ECHO_OFF text} and {@code PAM_PROMPT_ECHO_ON text} (an answer line follows),
 * {@code PAM_ERROR_MSG text}, {@code PAM_TEXT_INFO text}, and finally {@code SUCCESS} or {@code FAILURE}.
 * The texts are escaped like C strings (glib's g_strescape).
 */
final class Helper {
    static final Path SOCKET = Path.of("/run/polkit/agent-helper.socket");
    static final Path PROGRAM = Path.of("/usr/lib/polkit-1/polkit-agent-helper-1");
    private static final Logger LOGGER = Logger.getLogger(Helper.class.getName());

    /** What the helper asks and tells, answered by the dialog. */
    interface Conversation {
        /** The answer to a PAM prompt, or null to stop. The array is wiped after it was sent. */
        char[] ask(String prompt, boolean echo);

        void info(String text);

        void error(String text);
    }

    enum Outcome { SUCCESS, FAILURE, CANCELLED }

    private final Path socket;
    private final List<String> program;
    private volatile Runnable stop = () -> {};
    private volatile boolean cancelled;

    Helper() {
        this(SOCKET, List.of(PROGRAM.toString()));
    }

    /** {@code program} is the helper's command line without the user name. */
    Helper(Path socket, List<String> program) {
        this.socket = socket;
        this.program = program;
    }

    /** Authenticates {@code user} for {@code cookie}; blocks until the helper is done or {@link #cancel()}. */
    Outcome run(String user, String cookie, Conversation conversation) {
        try (Channel channel = open(user)) {
            stop = channel::close;
            if (cancelled) return Outcome.CANCELLED;
            writeLine(channel.out(), cookie.toCharArray());
            BufferedReader in = new BufferedReader(new InputStreamReader(channel.in(), StandardCharsets.UTF_8));
            String line;
            while ((line = in.readLine()) != null) {
                String text = unescape(line);
                if (text.startsWith("PAM_PROMPT_ECHO_OFF ") || text.startsWith("PAM_PROMPT_ECHO_ON ")) {
                    boolean echo = text.startsWith("PAM_PROMPT_ECHO_ON ");
                    char[] answer = conversation.ask(text.substring(text.indexOf(' ') + 1), echo);
                    if (answer == null) {
                        cancelled = true;
                        return Outcome.CANCELLED;
                    }
                    try {
                        writeLine(channel.out(), answer);
                    } finally {
                        Arrays.fill(answer, '\0');
                    }
                } else if (text.startsWith("PAM_ERROR_MSG ")) {
                    conversation.error(text.substring("PAM_ERROR_MSG ".length()));
                } else if (text.startsWith("PAM_TEXT_INFO ")) {
                    conversation.info(text.substring("PAM_TEXT_INFO ".length()));
                } else if (text.startsWith("SUCCESS")) {
                    return Outcome.SUCCESS;
                } else if (text.startsWith("FAILURE")) {
                    return Outcome.FAILURE;
                } else {
                    LOGGER.warning(() -> "Unknown line from the polkit helper: " + text);
                    return Outcome.FAILURE;
                }
            }
            return cancelled ? Outcome.CANCELLED : Outcome.FAILURE;
        } catch (IOException e) {
            if (cancelled) return Outcome.CANCELLED;
            LOGGER.log(Level.WARNING, "The polkit helper failed", e);
            return Outcome.FAILURE;
        } finally {
            stop = () -> {};
        }
    }

    /** Stops a running conversation from another thread; {@link #run} then returns CANCELLED. */
    void cancel() {
        cancelled = true;
        stop.run();
    }

    private Channel open(String user) throws IOException {
        if (Files.exists(socket)) {
            try {
                SocketChannel channel = SocketChannel.open(UnixDomainSocketAddress.of(socket));
                Channel opened = new Channel(Channels.newInputStream(channel), Channels.newOutputStream(channel),
                        () -> {
                            try {
                                channel.close();
                            } catch (IOException e) {
                                // closed already
                            }
                        });
                writeLine(opened.out(), user.toCharArray());
                return opened;
            } catch (IOException e) {
                LOGGER.log(Level.INFO, "Cannot use " + socket + ", starting the helper program instead", e);
            }
        }
        List<String> command = new java.util.ArrayList<>(program);
        command.add(user);
        Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        return new Channel(process.getInputStream(), process.getOutputStream(), process::destroy);
    }

    private static void writeLine(OutputStream out, char[] text) throws IOException {
        ByteBuffer bytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(text));
        byte[] line = new byte[bytes.remaining() + 1];
        bytes.get(line, 0, bytes.remaining());
        line[line.length - 1] = '\n';
        try {
            out.write(line);
            out.flush();
        } finally {
            Arrays.fill(line, (byte) 0);
            if (bytes.hasArray()) Arrays.fill(bytes.array(), (byte) 0);
        }
    }

    /** Undoes g_strescape: \b \f \n \r \t \v \\ \" and octal escapes (UTF-8 bytes of other characters). */
    static String unescape(String line) {
        if (line.indexOf('\\') < 0) return line;
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        byte[] raw = line.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < raw.length; i++) {
            byte b = raw[i];
            if (b != '\\' || i + 1 == raw.length) {
                bytes.write(b);
                continue;
            }
            byte next = raw[++i];
            if (next >= '0' && next <= '7') {
                int value = 0;
                int digits = 0;
                while (digits < 3 && i < raw.length && raw[i] >= '0' && raw[i] <= '7') {
                    value = value * 8 + (raw[i++] - '0');
                    digits++;
                }
                i--;
                bytes.write(value);
                continue;
            }
            bytes.write(switch (next) {
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'v' -> 0x0b;
                default -> next;
            });
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private record Channel(InputStream in, OutputStream out, Runnable closer) implements AutoCloseable {
        @Override
        public void close() {
            closer.run();
        }
    }
}
