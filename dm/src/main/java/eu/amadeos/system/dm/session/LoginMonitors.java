package eu.amadeos.system.dm.session;

import java.awt.Rectangle;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import eu.amadeos.system.display.DisplayApplier;
import eu.amadeos.system.display.DisplaySettingsStore;
import eu.amadeos.system.display.Output;

/**
 * The monitors of the login screen. Settings > Display keeps the user's monitor layout in
 * {@code ~/.config/amadeos/display.conf}, and the session sets it at login; before that the X server shows the
 * layout it starts with, so a laptop whose own screen was switched off greeted on that screen. The display
 * manager therefore sets the layout of the user it proposes (the last one who logged in) before the greeter
 * opens, and puts the login card on the primary monitor.
 *
 * <p>The display manager runs as root, so it can read every user's file. It reads only a regular file, not a
 * link, and the store accepts only numbers and the names of the connected monitors, which go to xrandr as
 * arguments, never through a shell.
 */
public final class LoginMonitors {
    private static final Logger LOGGER = Logger.getLogger(LoginMonitors.class.getName());

    private final DisplayApplier applier;

    public LoginMonitors() {
        this(new DisplayApplier());
    }

    LoginMonitors(DisplayApplier applier) {
        this.applier = applier;
    }

    /** The layout file of a user with this home folder. */
    static Path layoutFile(Path home) {
        return home.resolve(".config").resolve("amadeos").resolve(DisplaySettingsStore.FILE_NAME);
    }

    /**
     * Sets the layout the user kept for the monitors connected now; does nothing if the user is unknown or kept
     * none for them.
     */
    public void applyLayoutOf(String user) {
        if (user == null || user.isBlank()) return;
        Optional<Path> home = UserAccount.lookup(user).map(a -> Path.of(a.home()));
        home.ifPresent(this::applyLayoutIn);
    }

    /** Sets the layout kept in the {@code display.conf} under this home folder, if there is one. */
    void applyLayoutIn(Path home) {
        Path file = layoutFile(home);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return;
        if (!applier.applySaved(new DisplaySettingsStore(file))) {
            LOGGER.warning("Cannot set the monitor layout of " + file + " for the login screen");
        }
    }

    /** Where the login card goes: the primary monitor, else the leftmost one; empty if xrandr cannot tell. */
    public Optional<Rectangle> loginArea() {
        return loginArea(applier.query());
    }

    static Optional<Rectangle> loginArea(List<Output> outputs) {
        List<Output> on = outputs.stream().filter(o -> o.enabled() && o.width() > 0 && o.height() > 0).toList();
        return on.stream().filter(Output::primary).findFirst()
                .or(() -> on.stream().min(Comparator.comparingInt(Output::x).thenComparingInt(Output::y)))
                .map(LoginMonitors::bounds);
    }

    private static Rectangle bounds(Output o) {
        boolean portrait = o.rotation().portrait();
        return new Rectangle(o.x(), o.y(), portrait ? o.height() : o.width(), portrait ? o.width() : o.height());
    }
}
