package eu.amadeos.system.polkit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

import eu.amadeos.commons.i18n.LocaleResolution;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.ThemeStore;
import eu.amadeos.system.polkit.dbus.AuthenticationAgent;
import eu.amadeos.system.polkit.dbus.Authority;
import eu.amadeos.system.polkit.dbus.Subject;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBus;

/**
 * The polkit authentication agent of the session ({@code amadeos-session polkit-agent}, started by
 * {@code /etc/amadeos/autostart.d/30-amadeos-polkit-agent}): registers with polkitd for the login session
 * and asks for the password whenever a program of the session needs authorization, e.g. Software through
 * pkexec. Registers again when polkitd restarts (an upgrade of polkitd does that).
 *
 * <p>{@code --process PID} registers for one process and its children instead of the session, like
 * {@code pkttyagent --process}; for tests without a logind session.
 *
 * <p>Exit codes: 0 when the system bus went away, 1 no session to serve, 2 usage, 3 another agent is
 * registered for the session already, 4 polkitd not reachable.
 */
public final class PolkitAgent {
    static final String OBJECT_PATH = "/eu/amadeos/PolkitAgent";
    static final String TEXTS = "eu.amadeos.system.polkit.agent";
    private static final Logger LOGGER = Logger.getLogger(PolkitAgent.class.getName());

    private final DBusConnection bus;
    private final Subject subject;
    private final String locale;

    private PolkitAgent(DBusConnection bus, Subject subject, String locale) {
        this.bus = bus;
        this.subject = subject;
        this.locale = locale;
    }

    public static void main(String[] args) {
        Optional<Subject> subject = subject(args, System.getenv());
        if (subject.isEmpty()) {
            System.err.println("Usage: amadeos-session polkit-agent [--process PID] (needs XDG_SESSION_ID otherwise)");
            System.exit(args.length > 0 ? 2 : 1);
        }
        Locale language = LocaleResolution.resolve(Optional.empty(), Optional.empty(), System.getenv())
                .language().locale();
        Messages msg = Messages.load(PolkitAgent.class, TEXTS, language);
        ThemeStore theme = ThemeStore.system();
        try {
            SwingUtilities.invokeAndWait(() -> {
                AmadeUI.install(theme.current());
                AmadeUI.follow(theme);
            });
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Cannot set up the user interface", e);
            System.exit(1);
        }

        Authenticator authenticator = new Authenticator(new AuthenticationDialog(msg), Helper::new);
        String self = System.getProperty("user.name", "");
        try (DBusConnection bus = DBusConnectionBuilder.forSystemBus().build()) {
            bus.exportObject(OBJECT_PATH, new Agent(authenticator, () -> Users.read(Path.of("/etc/passwd")), self));
            PolkitAgent agent = new PolkitAgent(bus, subject.get(), polkitLocale(language));
            bus.addSigHandler(DBus.NameOwnerChanged.class, signal -> {
                if (!Authority.BUS_NAME.equals(signal.name)) return;
                if (!signal.newOwner.isEmpty()) {
                    LOGGER.info("polkitd restarted; registering again");
                    agent.register();
                }
            });
            int code = agent.register();
            if (code != 0) System.exit(code);
            LOGGER.info(() -> "Registered as the polkit authentication agent for " + subject.get());
            while (bus.isConnected()) {
                Thread.sleep(5_000);
            }
            System.exit(0);
        } catch (DBusException e) {
            LOGGER.log(Level.SEVERE, "Cannot connect to the system bus", e);
            System.exit(4);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "The polkit agent stopped", e);
            System.exit(4);
        }
    }

    /** Registers with polkitd; 0, or the exit code when that failed. */
    private int register() {
        try {
            Authority authority = bus.getRemoteObject(Authority.BUS_NAME, Authority.PATH, Authority.class);
            authority.registerAuthenticationAgent(subject, locale, OBJECT_PATH);
            return 0;
        } catch (DBusExecutionException e) {
            // polkitd: "An authentication agent already exists for the given subject".
            LOGGER.log(Level.WARNING, "polkitd refused the agent: " + e.getMessage());
            return 3;
        } catch (DBusException e) {
            LOGGER.log(Level.SEVERE, "polkitd is not reachable", e);
            return 4;
        }
    }

    /** The subject to serve: {@code --process PID}, else the login session of {@code XDG_SESSION_ID}. */
    static Optional<Subject> subject(String[] args, Map<String, String> environment) {
        if (args.length == 2 && args[0].equals("--process")) {
            try {
                long pid = Long.parseLong(args[1]);
                return startTime(Files.readString(Path.of("/proc", args[1], "stat")))
                        .map(start -> Subject.process(pid, start));
            } catch (NumberFormatException | IOException e) {
                return Optional.empty();
            }
        }
        if (args.length > 0) return Optional.empty();
        String session = environment.getOrDefault("XDG_SESSION_ID", "");
        return session.isBlank() ? Optional.empty() : Optional.of(Subject.session(session));
    }

    /** Field 22 of /proc/PID/stat; the command name in field 2 may contain spaces and parentheses. */
    static Optional<Long> startTime(String stat) {
        String[] fields = stat.substring(stat.lastIndexOf(')') + 2).trim().split(" ");
        try {
            return Optional.of(Long.parseLong(fields[19]));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** The locale polkitd translates its messages into (the .policy files' xml:lang), e.g. de_DE.UTF-8. */
    static String polkitLocale(Locale locale) {
        String tag = locale.getCountry().isEmpty() ? locale.getLanguage()
                : locale.getLanguage() + "_" + locale.getCountry();
        return tag + ".UTF-8";
    }

    /** The object polkitd calls; methods run on dbus-java's worker threads. */
    static final class Agent implements AuthenticationAgent {
        private final Authenticator authenticator;
        private final java.util.function.Supplier<Map<Long, Users.User>> accounts;
        private final String self;

        Agent(Authenticator authenticator, java.util.function.Supplier<Map<Long, Users.User>> accounts,
              String self) {
            this.authenticator = authenticator;
            this.accounts = accounts;
            this.self = self;
        }

        @Override
        public void beginAuthentication(String actionId, String message, String iconName,
                                        Map<String, String> details, String cookie, List<Subject> identities) {
            LOGGER.info(() -> "Authentication requested for " + actionId);
            List<Users.User> users = Users.of(identities, accounts.get(), self);
            authenticator.authenticate(new Authenticator.Request(actionId, message, iconName, details, cookie,
                    users));
        }

        @Override
        public void cancelAuthentication(String cookie) {
            authenticator.cancel(cookie);
        }

        @Override
        public String getObjectPath() {
            return OBJECT_PATH;
        }
    }
}
