package eu.amadeos.system.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Only the two scheme pictures follow the Horizon scheme; the extra Horizon wallpapers stay where the user put them. */
class LookPageTest {
    @Test
    void onlyTheSchemePicturesFollowTheScheme() {
        assertTrue(LookPage.isSchemePicture("/usr/share/backgrounds/amadeos/horizon-dark.png"));
        assertTrue(LookPage.isSchemePicture("/usr/share/backgrounds/amadeos/horizon-light.png"));
        assertFalse(LookPage.isSchemePicture("/usr/share/backgrounds/amadeos/horizon-dunes.jpg"));
        assertFalse(LookPage.isSchemePicture("/usr/share/backgrounds/amadeos/halloween.png"));
        assertFalse(LookPage.isSchemePicture(null));
    }
}
