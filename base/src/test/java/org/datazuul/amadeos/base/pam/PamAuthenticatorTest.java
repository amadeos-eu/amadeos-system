package org.datazuul.amadeos.base.pam;

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

    @Test
    void formerBuiltInLiveCredentialsNoLongerBypassPam() {
        // Without mock mode every login goes through PAM; the service name is unknown here,
        // so PAM falls back to the system "other" policy and must reject these attempts.
        PamAuthenticator auth = new PamAuthenticator("amadeos-test-no-such-service");
        for (String password : new String[]{"", "live", "amadeos", "amadeoz"}) {
            PamAuthenticator.AuthResult login = auth.authenticate("amadeos", password);
            assertFalse(login.success(), "login must fail for password '" + password + "'");
            assertNull(login.session());
            assertFalse(auth.verify("amadeos", password).success(), "unlock must fail for password '" + password + "'");
        }
    }

    @Test
    void mockModeNoLongerAcceptsEmptyOrLivePasswords() {
        System.setProperty("amadeos.mock.pam", "true");
        try {
            PamAuthenticator auth = new PamAuthenticator();
            assertFalse(auth.authenticate("amadeos", "").success());
            assertFalse(auth.authenticate("amadeos", "live").success());
            assertFalse(auth.authenticate("someoneelse", "amadeos").success());
        } finally {
            System.clearProperty("amadeos.mock.pam");
        }
    }

    @Test
    void verifyAcceptsMockCredentials() {
        System.setProperty("amadeos.mock.pam", "true");
        try {
            PamAuthenticator.AuthResult result = new PamAuthenticator().verify("amadeos", "amadeos");
            assertTrue(result.success());
        } finally {
            System.clearProperty("amadeos.mock.pam");
        }
    }
}
