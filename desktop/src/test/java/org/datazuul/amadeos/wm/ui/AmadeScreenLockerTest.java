package org.datazuul.amadeos.wm.ui;

import java.awt.GraphicsEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmadeScreenLockerTest {

    @BeforeEach
    @AfterEach
    void resetState() {
        AmadeScreenLocker.forceUnlockForTesting();
    }

    @Test
    void screenLockerInitiallyUnlocked() {
        assertFalse(AmadeScreenLocker.isLocked());
    }

    @Test
    void lockScreenSetsLockedState() throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }

        AmadeScreenLocker.lockScreen();
        Thread.sleep(100);
        assertTrue(AmadeScreenLocker.isLocked());

        AmadeScreenLocker.forceUnlockForTesting();
        Thread.sleep(100);
        assertFalse(AmadeScreenLocker.isLocked());
    }
}
