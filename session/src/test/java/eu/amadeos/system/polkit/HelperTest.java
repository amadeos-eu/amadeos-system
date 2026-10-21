package eu.amadeos.system.polkit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelperTest {
    /**
     * Behaves like polkit-agent-helper-1: the user is the argument, the cookie the first line; one password
     * prompt, "secret" is right. Writes what it got to the file in $SEEN.
     */
    static final String FAKE_HELPER = """
            read -r cookie
            echo "$1 $cookie" > "$SEEN"
            echo 'PAM_TEXT_INFO Hello\\040there'
            echo 'PAM_PROMPT_ECHO_OFF Password: '
            read -r password
            if [ "$password" = secret ]; then echo SUCCESS; else echo 'PAM_ERROR_MSG Wrong \\"password\\"'; echo FAILURE; fi
            """;

    @TempDir
    Path dir;

    static Path fakeHelper(Path dir) throws Exception {
        return Files.writeString(dir.resolve("helper.sh"), FAKE_HELPER);
    }

    static Helper helper(Path dir) throws Exception {
        Path script = fakeHelper(dir);
        return new Helper(dir.resolve("no-socket"), List.of("env", "SEEN=" + dir.resolve("seen"), "bash",
                script.toString()));
    }

    /** Answers every prompt with the given password and records the rest. */
    static class Script implements Helper.Conversation {
        final List<String> events = new CopyOnWriteArrayList<>();
        private final String password;

        Script(String password) {
            this.password = password;
        }

        @Override
        public char[] ask(String prompt, boolean echo) {
            events.add("ask " + prompt + " echo=" + echo);
            return password == null ? null : password.toCharArray();
        }

        @Override
        public void info(String text) {
            events.add("info " + text);
        }

        @Override
        public void error(String text) {
            events.add("error " + text);
        }
    }

    @Test
    void theRightPasswordSucceeds() throws Exception {
        Script script = new Script("secret");
        assertEquals(Helper.Outcome.SUCCESS, helper(dir).run("alice", "cookie-1", script));
        assertEquals(List.of("info Hello there", "ask Password:  echo=false"), script.events);
        assertEquals("alice cookie-1", Files.readString(dir.resolve("seen")).strip());
    }

    @Test
    void aWrongPasswordFailsWithPamsMessage() throws Exception {
        Script script = new Script("guess");
        assertEquals(Helper.Outcome.FAILURE, helper(dir).run("alice", "c", script));
        assertTrue(script.events.contains("error Wrong \"password\""), script.events.toString());
    }

    @Test
    void noAnswerCancels() throws Exception {
        assertEquals(Helper.Outcome.CANCELLED, helper(dir).run("alice", "c", new Script(null)));
    }

    @Test
    void cancelFromAnotherThreadEndsAWaitingRun() throws Exception {
        Helper helper = helper(dir);
        CountDownLatch asked = new CountDownLatch(1);
        CompletableFuture<char[]> never = new CompletableFuture<>();
        Helper.Conversation waiting = new Script("x") {
            @Override
            public char[] ask(String prompt, boolean echo) {
                asked.countDown();
                return never.join();
            }
        };
        CompletableFuture<Helper.Outcome> outcome = CompletableFuture.supplyAsync(
                () -> helper.run("alice", "c", waiting));
        assertTrue(asked.await(5, TimeUnit.SECONDS));
        helper.cancel();
        never.complete(null);
        assertEquals(Helper.Outcome.CANCELLED, outcome.get(5, TimeUnit.SECONDS));
    }

    @Test
    void theSocketOfNewerPolkitGetsTheUserFirst() throws Exception {
        Path socket = dir.resolve("agent-helper.socket");
        List<String> received = new ArrayList<>();
        try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            server.bind(UnixDomainSocketAddress.of(socket));
            CompletableFuture<Void> served = CompletableFuture.runAsync(() -> {
                try (SocketChannel client = server.accept()) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(Channels.newInputStream(client),
                            StandardCharsets.UTF_8));
                    PrintWriter out = new PrintWriter(Channels.newOutputStream(client), true, StandardCharsets.UTF_8);
                    received.add(in.readLine());
                    received.add(in.readLine());
                    out.println("PAM_PROMPT_ECHO_OFF Passwort: ");
                    received.add(in.readLine());
                    out.println("SUCCESS");
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            Helper helper = new Helper(socket, List.of("false"));
            assertEquals(Helper.Outcome.SUCCESS, helper.run("bob", "cookie-2", new Script("Grüße")));
            served.get(5, TimeUnit.SECONDS);
        }
        assertEquals(List.of("bob", "cookie-2", "Grüße"), received);
    }

    @Test
    void helperTextsAreUnescapedLikeGlib() {
        assertEquals("plain", Helper.unescape("plain"));
        assertEquals("a\nb\t\"c\"\\", Helper.unescape("a\\nb\\t\\\"c\\\"\\\\"));
        // g_strescape writes non-ASCII as octal UTF-8 bytes.
        assertEquals("Passwort für Root:", Helper.unescape("Passwort f\\303\\274r Root:"));
    }
}
