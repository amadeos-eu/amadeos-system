package eu.amadeos.system.polkit;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import eu.amadeos.system.polkit.Authenticator.Answer;
import eu.amadeos.system.polkit.Authenticator.Request;
import eu.amadeos.system.polkit.Users.User;
import org.freedesktop.PolicyKit1.Error;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticatorTest {
    private static final User ALICE = new User(1000, "alice", "Alice");
    private static final User ROOT = new User(0, "root", "root");

    @TempDir
    Path dir;

    /** Answers prompts from a queue and records what the dialog was told. */
    static final class ScriptedPrompter implements Authenticator.Prompter {
        final List<String> events = new CopyOnWriteArrayList<>();
        final Deque<Answer> answers = new ArrayDeque<>();
        volatile CountDownLatch asking = new CountDownLatch(1);
        volatile CompletableFuture<Answer> held;

        ScriptedPrompter answer(Answer... next) {
            answers.addAll(List.of(next));
            return this;
        }

        @Override
        public void open(Request request) {
            events.add("open " + request.actionId());
        }

        @Override
        public synchronized Answer ask(User user, String prompt, boolean echo) {
            events.add("ask " + user.login());
            Answer next = answers.poll();
            if (next != null) return next;
            // Nothing scripted: wait like a user who does not type, until close() or the test answers.
            held = new CompletableFuture<>();
            asking.countDown();
            return held.join();
        }

        @Override
        public void info(String text) {
            events.add("info " + text);
        }

        @Override
        public void error(String text) {
            events.add("error " + text);
        }

        @Override
        public void failed() {
            events.add("failed");
        }

        @Override
        public void close() {
            events.add("close");
            CompletableFuture<Answer> waiting = held;
            if (waiting != null) waiting.complete(new Answer.Cancel());
        }
    }

    private Request request(List<User> users) {
        return new Request("org.example.pkexec", "Authentication is needed", "", Map.of("program", "/bin/true"),
                "cookie-" + System.nanoTime(), users);
    }

    private Authenticator authenticator(ScriptedPrompter prompter) throws Exception {
        HelperTest.fakeHelper(dir);
        return new Authenticator(prompter, () -> {
            try {
                return HelperTest.helper(dir);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static Answer.Text text(String password) {
        return new Answer.Text(password.toCharArray());
    }

    @Test
    void aWrongPasswordIsAskedAgainUntilItIsRight() throws Exception {
        ScriptedPrompter prompter = new ScriptedPrompter().answer(text("guess"), text("secret"));
        authenticator(prompter).authenticate(request(List.of(ALICE)));
        assertEquals(List.of("open org.example.pkexec", "info Hello there", "ask alice",
                "error Wrong \"password\"", "failed", "info Hello there", "ask alice", "close"), prompter.events);
    }

    @Test
    void cancellingTellsPolkitdDismissed() throws Exception {
        ScriptedPrompter prompter = new ScriptedPrompter().answer(new Answer.Cancel());
        assertThrows(Error.Cancelled.class, () -> authenticator(prompter).authenticate(request(List.of(ALICE))));
        assertEquals("close", prompter.events.getLast());
    }

    @Test
    void anotherUserStartsTheHelperAgainForThatUser() throws Exception {
        ScriptedPrompter prompter = new ScriptedPrompter().answer(new Answer.SwitchUser(ROOT), text("secret"));
        authenticator(prompter).authenticate(request(List.of(ALICE, ROOT)));
        assertEquals(List.of("ask alice", "ask root"),
                prompter.events.stream().filter(e -> e.startsWith("ask")).toList());
        assertTrue(java.nio.file.Files.readString(dir.resolve("seen")).startsWith("root "));
    }

    @Test
    void polkitdCanCancelARunningRequest() throws Exception {
        ScriptedPrompter prompter = new ScriptedPrompter();
        Authenticator authenticator = authenticator(prompter);
        Request request = request(List.of(ALICE));
        CompletableFuture<Void> running = CompletableFuture.runAsync(() -> authenticator.authenticate(request));
        assertTrue(prompter.asking.await(5, TimeUnit.SECONDS));
        authenticator.cancel(request.cookie());
        ExecutionException failure = assertThrows(ExecutionException.class, () -> running.get(5, TimeUnit.SECONDS));
        assertInstanceOf(Error.Cancelled.class, failure.getCause());
    }

    @Test
    void aRequestCancelledBeforeItsTurnIsNotShown() throws Exception {
        ScriptedPrompter prompter = new ScriptedPrompter();
        Authenticator authenticator = authenticator(prompter);
        Request request = request(List.of(ALICE));
        authenticator.cancel(request.cookie());
        assertThrows(Error.Cancelled.class, () -> authenticator.authenticate(request));
        assertTrue(prompter.events.stream().noneMatch(e -> e.startsWith("open")));
    }

    @Test
    void withoutUsersOrHelperTheRequestFails() throws Exception {
        ScriptedPrompter prompter = new ScriptedPrompter();
        assertThrows(Error.Failed.class, () -> authenticator(prompter).authenticate(request(List.of())));
        Authenticator broken = new Authenticator(prompter,
                () -> new Helper(dir.resolve("no-socket"), List.of(dir.resolve("missing-helper").toString())));
        assertThrows(Error.Failed.class, () -> broken.authenticate(request(List.of(ALICE))));
    }
}
