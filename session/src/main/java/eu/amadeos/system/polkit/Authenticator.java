package eu.amadeos.system.polkit;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.logging.Logger;

import eu.amadeos.system.polkit.Users.User;
import org.freedesktop.PolicyKit1.Error;

/**
 * Handles polkitd's requests one at a time: shows the dialog, runs polkit's helper for the chosen user and
 * passes the PAM prompts to the dialog, and starts again after a wrong password until the user authenticates
 * or gives up. polkitd can cancel a request at any time (the program that asked has ended, for example).
 */
final class Authenticator {
    private static final Logger LOGGER = Logger.getLogger(Authenticator.class.getName());

    /** One request of polkitd; {@code message} is already in the agent's language. */
    record Request(String actionId, String message, String iconName, Map<String, String> details, String cookie,
                   List<User> users) {
    }

    /** What the user did at a prompt. */
    sealed interface Answer {
        record Text(char[] chars) implements Answer {
        }

        record Cancel() implements Answer {
        }

        /** Another user was chosen: the helper is started again for that one. */
        record SwitchUser(User user) implements Answer {
        }
    }

    /** The dialog; {@link AuthenticationDialog} on screen, a script in the tests. */
    interface Prompter {
        void open(Request request);

        /** Blocks until the user answered {@code prompt} for {@code user}, or the dialog was closed. */
        Answer ask(User user, String prompt, boolean echo);

        void info(String text);

        void error(String text);

        /** The password was wrong; the next prompt follows. */
        void failed();

        /** Hides the dialog; a waiting {@link #ask} returns {@link Answer.Cancel}. */
        void close();
    }

    private final Prompter prompter;
    private final Supplier<Helper> helpers;
    private final ReentrantLock turn = new ReentrantLock(true);
    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();
    private volatile String activeCookie;
    private volatile Helper activeHelper;

    Authenticator(Prompter prompter, Supplier<Helper> helpers) {
        this.prompter = prompter;
        this.helpers = helpers;
    }

    /** Returns once polkitd's cookie is authenticated; throws Cancelled when the user gave up. */
    void authenticate(Request request) {
        if (request.users().isEmpty()) {
            throw new Error.Failed("No user to authenticate as for " + request.actionId());
        }
        turn.lock();
        try {
            if (cancelled.remove(request.cookie())) throw new Error.Cancelled("Cancelled by polkitd");
            activeCookie = request.cookie();
            prompter.open(request);
            User user = request.users().getFirst();
            while (true) {
                Run run = new Run(user);
                Helper helper = helpers.get();
                activeHelper = helper;
                if (cancelled.contains(request.cookie())) helper.cancel();
                Helper.Outcome outcome = helper.run(user.login(), request.cookie(), run);
                switch (outcome) {
                    case SUCCESS -> {
                        LOGGER.info(() -> "Authenticated as " + run.user.login() + " for " + request.actionId());
                        return;
                    }
                    case FAILURE -> {
                        // Without a prompt the helper could not even start: trying again would only loop.
                        if (!run.asked) throw new Error.Failed("The polkit helper failed");
                        prompter.failed();
                    }
                    case CANCELLED -> {
                        if (run.switchTo == null) throw new Error.Cancelled("Dismissed by the user");
                        user = run.switchTo;
                    }
                }
            }
        } finally {
            activeHelper = null;
            activeCookie = null;
            cancelled.remove(request.cookie());
            prompter.close();
            turn.unlock();
        }
    }

    /** polkitd's CancelAuthentication: ends the request with that cookie, running or still waiting. */
    void cancel(String cookie) {
        cancelled.add(cookie);
        if (cookie.equals(activeCookie)) {
            Helper helper = activeHelper;
            if (helper != null) helper.cancel();
            prompter.close();
        }
    }

    /** One run of the helper: the prompts go to the dialog. */
    private final class Run implements Helper.Conversation {
        private final User user;
        private boolean asked;
        private User switchTo;

        Run(User user) {
            this.user = user;
        }

        @Override
        public char[] ask(String prompt, boolean echo) {
            asked = true;
            return switch (prompter.ask(user, prompt, echo)) {
                case Answer.Text text -> text.chars();
                case Answer.Cancel cancel -> null;
                case Answer.SwitchUser other -> {
                    switchTo = other.user();
                    yield null;
                }
            };
        }

        @Override
        public void info(String text) {
            prompter.info(text);
        }

        @Override
        public void error(String text) {
            prompter.error(text);
        }
    }
}
