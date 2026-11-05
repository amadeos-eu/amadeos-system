package eu.amadeos.system.lock;

import java.awt.GraphicsEnvironment;
import javax.swing.SwingUtilities;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.commons.theme.HorizonThemes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class LockScreenUiTest {
    @Test
    void theLockScreenHasTheDesktopsLook() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs an X display");
        boolean[] horizon = new boolean[2];
        SwingUtilities.invokeAndWait(() -> {
            LockScreenUi flat = new LockScreenUi("ralf",
                    HorizonThemes.theme(HorizonThemes.Scheme.DARK, HorizonThemes.Accent.TEAL));
            horizon[0] = flat.content() instanceof HorizonLockPane;
            flat.setPasswordLength(3);
            flat.setStatus("Wrong password", true);
            flat.dispose();
            LockScreenUi classic = new LockScreenUi("ralf", DesktopTheme.WARM_MOTIF);
            horizon[1] = classic.content() instanceof HorizonLockPane;
            classic.dispose();
        });
        assertTrue(horizon[0], "Horizon: time, round picture and password field over Horizon's picture");
        assertFalse(horizon[1], "classic: the Motif card");
    }
}
