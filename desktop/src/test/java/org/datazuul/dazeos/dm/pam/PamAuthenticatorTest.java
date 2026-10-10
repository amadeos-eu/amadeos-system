package org.datazuul.dazeos.dm.pam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PamAuthenticatorTest {

    @Test
    void testEmptyUsernameFails() {
        PamAuthenticator auth = new PamAuthenticator();
        PamAuthenticator.AuthResult res = auth.authenticate("", "password");
        assertFalse(res.success());
        assertNotNull(res.errorMessage());
    }

    @Test
    void testNullUsernameFails() {
        PamAuthenticator auth = new PamAuthenticator();
        PamAuthenticator.AuthResult res = auth.authenticate(null, "password");
        assertFalse(res.success());
        assertNotNull(res.errorMessage());
    }

    @Test
    void testMockPamAuthenticationSucceedsForDefaultCredentials() {
        System.setProperty("dazeos.mock.pam", "true");
        try {
            PamAuthenticator auth = new PamAuthenticator();
            PamAuthenticator.AuthResult res = auth.authenticate("dazeos", "dazeos");
            assertTrue(res.success());
            assertNotNull(res.session());
            assertEquals("dazeos", res.session().username());
            // Safe to close
            res.session().close();
        } finally {
            System.clearProperty("dazeos.mock.pam");
        }
    }

    @Test
    void testMockPamAuthenticationRejectsWrongPassword() {
        System.setProperty("dazeos.mock.pam", "true");
        try {
            PamAuthenticator auth = new PamAuthenticator();
            PamAuthenticator.AuthResult res = auth.authenticate("dazeos", "wrongpassword");
            assertFalse(res.success());
            assertNull(res.session());
            assertTrue(res.errorMessage().contains("Invalid username or password"));
        } finally {
            System.clearProperty("dazeos.mock.pam");
        }
    }
}

