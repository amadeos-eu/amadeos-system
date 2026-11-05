package eu.amadeos.system.dm.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import eu.amadeos.commons.theme.DesktopLook;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.commons.theme.HorizonThemes;
import eu.amadeos.commons.theme.ThemeRegistry;
import eu.amadeos.commons.theme.ThemeStore;
import eu.amadeos.system.dm.session.SessionType;

/**
 * The look of the login greeter: the look the chosen session will have. Classic (CDE) shows the greeter in the
 * Motif look, Horizon in the flat one. The desktop session keeps the look of the user's last session, so the
 * greeter takes it from the user's {@code ~/.config/amadeos/look.conf}; a user who never chose one starts with
 * Horizon, as amadeos-session decides. The greeter runs as root, so it can read every user's files.
 */
final class GreeterLook {
    /** Horizon's default colours, for an unknown user. */
    static final DesktopTheme HORIZON_DEFAULT =
            HorizonThemes.theme(HorizonThemes.DEFAULT_SCHEME, HorizonThemes.DEFAULT_ACCENT);

    private GreeterLook() {
    }

    /**
     * The theme of the greeter for a session.
     *
     * @param session the session field's choice
     * @param home    the home of the user in the user field, if the user exists
     * @param classic the greeter's own classic palette, for users without one of their own
     */
    static DesktopTheme themeFor(SessionType session, Optional<Path> home, DesktopTheme classic) {
        Optional<Path> config = home.map(h -> h.resolve(".config").resolve("amadeos"));
        Properties look = config.map(ThemeStore::readLook).orElseGet(Properties::new);
        DesktopTheme horizon = HorizonThemes.theme(HorizonThemes.Scheme.of(look.getProperty("scheme")),
                HorizonThemes.Accent.of(look.getProperty("accent")));
        DesktopTheme ownClassic = config.filter(c -> Files.isRegularFile(c.resolve(ThemeStore.THEME_FILE)))
                .map(GreeterLook::storedTheme).filter(t -> t.look() == DesktopLook.CLASSIC).orElse(classic);
        return switch (session) {
            case CLASSIC -> ownClassic;
            case HORIZON -> horizon;
            case DESKTOP, FAILSAFE -> {
                String chosen = look.getProperty("look");
                if (chosen != null) {
                    yield DesktopLook.of(chosen) == DesktopLook.HORIZON ? horizon : ownClassic;
                }
                // No look chosen yet: a classic palette of an older amadeOS keeps the classic look
                boolean oldClassicUser = config.map(c -> Files.isRegularFile(c.resolve(ThemeStore.THEME_FILE)))
                        .orElse(false);
                yield oldClassicUser ? ownClassic : horizon;
            }
        };
    }

    /** The classic palette named in the user's {@code theme.conf}, whatever look the user has now. */
    private static DesktopTheme storedTheme(Path config) {
        try {
            String id = Files.readString(config.resolve(ThemeStore.THEME_FILE)).strip();
            ThemeRegistry registry = ThemeRegistry.withCustomThemes(
                    List.of(Path.of("/etc/amadeos/themes"), config.resolve("themes")));
            return registry.find(id).orElse(DesktopTheme.WARM_MOTIF);
        } catch (java.io.IOException | RuntimeException e) {
            return DesktopTheme.WARM_MOTIF;
        }
    }

    /** The greeter's own classic palette: the desktop default if root's settings name a Horizon theme. */
    static DesktopTheme classic(DesktopTheme current) {
        return current == null || current.look() != DesktopLook.CLASSIC ? DesktopTheme.WARM_MOTIF : current;
    }
}
