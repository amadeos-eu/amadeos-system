package org.datazuul.amadeos.dm.pam;

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
        System.setProperty("amadeos.mock.pam", "true");
        try {
            PamAuthenticator auth = new PamAuthenticator();
            PamAuthenticator.AuthResult res = auth.authenticate("amadeos", "amadeos");
            assertTrue(res.success());
            assertNotNull(res.session());
            assertEquals("amadeos", res.session().username());
            res.session().close();

            PamAuthenticator.AuthResult res2 = auth.authenticate("amadeos", "amadeoz");
            assertTrue(res2.success());
            res2.session().close();
        } finally {
            System.clearProperty("amadeos.mock.pam");
        }
    }

    @Test
    void testMockPamAuthenticationRejectsWrongPassword() {
        System.setProperty("amadeos.mock.pam", "true");
        try {
            PamAuthenticator auth = new PamAuthenticator();
            PamAuthenticator.AuthResult res = auth.authenticate("amadeos", "wrongpassword");
            assertFalse(res.success());
            assertNull(res.session());
            assertTrue(res.errorMessage().contains("Invalid username or password"));
        } finally {
            System.clearProperty("amadeos.mock.pam");
        }
    }

    @Test
    void testMockPamAcceptsQwertzSwap() {
        System.setProperty("amadeos.mock.pam", "true");
        try {
            PamAuthenticator auth = new PamAuthenticator();
            PamAuthenticator.AuthResult res = auth.authenticate("amadeos", "amadeoz");
            assertTrue(res.success());
            assertNotNull(res.session());
            res.session().close();
        } finally {
            System.clearProperty("amadeos.mock.pam");
        }
    }
}

