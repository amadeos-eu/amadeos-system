package eu.amadeos.system.base.panel;

import eu.amadeos.commons.theme.DesktopLook;
import eu.amadeos.system.base.theme.ThemeManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * The hand-over between the panels of the two desktop looks. amadeos-session starts the panel of the look in
 * {@code look.conf}; when the look changes (Settings &gt; Appearance), the running panel ends with
 * {@link #EXIT_OTHER_LOOK} and the session starts the other one, without logging out. Any other exit of the
 * panel still ends the session.
 */
public final class PanelSwitch {
    private static final Logger LOGGER = Logger.getLogger(PanelSwitch.class.getName());

    /** The panel's exit status that asks amadeos-session for the other look's panel (EX_TEMPFAIL). */
    public static final int EXIT_OTHER_LOOK = 75;
    /** Horizon's top bar; with it missing the classic Front Panel stays whatever the look. */
    public static final Path HORIZON_PANEL = Path.of("/usr/bin/amadeos-panel-horizon");

    private PanelSwitch() {
    }

    // --8<-- [start:watch]
    /**
     * Ends this panel once the desktop look is another than {@code own} and the other look has a panel to take
     * over: Horizon's top bar needs package amadeos-panel-horizon, the Front Panel is always there.
     */
    public static void watch(DesktopLook own) {
        ThemeManager.addListener(theme -> {
            DesktopLook look = theme.look();
            if (look == own) return;
            if (look == DesktopLook.HORIZON && !Files.isExecutable(HORIZON_PANEL)) {
                LOGGER.info("Horizon look without the top bar (amadeos-panel-horizon): the Front Panel stays");
                return;
            }
            LOGGER.info(() -> "Desktop look changed to " + look.id() + ": handing over to its panel");
            System.exit(EXIT_OTHER_LOOK);
        });
    }
    // --8<-- [end:watch]
}
