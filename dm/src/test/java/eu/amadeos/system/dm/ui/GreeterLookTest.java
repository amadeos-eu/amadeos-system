package eu.amadeos.system.dm.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import eu.amadeos.commons.theme.DesktopLook;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.dm.session.SessionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GreeterLookTest {
    private static final DesktopTheme CLASSIC = DesktopTheme.WARM_MOTIF;

    private static Path config(Path home, String look, String theme) throws Exception {
        Path dir = Files.createDirectories(home.resolve(".config/amadeos"));
        if (look != null) Files.writeString(dir.resolve("look.conf"), look);
        if (theme != null) Files.writeString(dir.resolve("theme.conf"), theme);
        return home;
    }

    @Test
    void theClassicSessionShowsTheMotifLook(@TempDir Path home) throws Exception {
        config(home, "look=horizon\n", null);
        assertEquals(DesktopLook.CLASSIC, GreeterLook.themeFor(SessionType.CLASSIC, Optional.of(home), CLASSIC).look());
        assertEquals(CLASSIC, GreeterLook.themeFor(SessionType.CLASSIC, Optional.empty(), CLASSIC));
    }

    @Test
    void theHorizonSessionShowsTheUsersColours(@TempDir Path home) throws Exception {
        config(home, "look=classic\nscheme=light\naccent=amber\n", null);
        assertEquals("horizon-light-amber", GreeterLook.themeFor(SessionType.HORIZON, Optional.of(home), CLASSIC).id());
        assertEquals("horizon-dark-teal", GreeterLook.themeFor(SessionType.HORIZON, Optional.empty(), CLASSIC).id());
    }

    @Test
    void theGreeterShowsTheUsersHorizonPalette(@TempDir Path home) throws Exception {
        config(home, "look=horizon\nscheme=light\naccent=amber\npalette=mist\n", null);
        assertEquals("horizon-light-amber-mist",
                GreeterLook.themeFor(SessionType.DESKTOP, Optional.of(home), CLASSIC).id());
    }

    @Test
    void theDesktopSessionKeepsTheLastLook(@TempDir Path home) throws Exception {
        config(home, "look=classic\n", "cde-desert\n");
        DesktopTheme last = GreeterLook.themeFor(SessionType.DESKTOP, Optional.of(home), CLASSIC);
        assertEquals("cde-desert", last.id(), "the user's own classic palette");

        Files.writeString(home.resolve(".config/amadeos/look.conf"), "look=horizon\nscheme=dark\naccent=blue\n");
        assertEquals("horizon-dark-blue", GreeterLook.themeFor(SessionType.DESKTOP, Optional.of(home), CLASSIC).id());
    }

    @Test
    void aUserWhoNeverChoseStartsWithHorizon(@TempDir Path home) throws Exception {
        assertEquals(DesktopLook.HORIZON, GreeterLook.themeFor(SessionType.DESKTOP, Optional.of(home), CLASSIC).look());
        assertEquals(DesktopLook.HORIZON, GreeterLook.themeFor(SessionType.DESKTOP, Optional.empty(), CLASSIC).look());
        config(home, null, "warm-motif\n");
        assertEquals(DesktopLook.CLASSIC, GreeterLook.themeFor(SessionType.DESKTOP, Optional.of(home), CLASSIC).look(),
                "a classic palette from an older amadeOS keeps the classic look, as amadeos-session does");
    }

    @Test
    void theGreetersOwnPaletteIsClassic() {
        assertEquals(CLASSIC, GreeterLook.classic(GreeterLook.HORIZON_DEFAULT));
        assertEquals(DesktopTheme.CDE_DESERT, GreeterLook.classic(DesktopTheme.CDE_DESERT));
    }

    @Test
    void theAvatarShowsTheFirstLetter() {
        assertEquals("R", AmadeLoginGreeter.initial("ralf"));
        assertEquals("?", AmadeLoginGreeter.initial("  "));
    }
}
