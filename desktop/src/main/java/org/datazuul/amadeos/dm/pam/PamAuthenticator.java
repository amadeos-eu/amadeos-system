package org.datazuul.amadeos.dm.pam;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.datazuul.amadeos.wm.x11.LibC;

public final class PamAuthenticator {
    private static final Logger LOGGER = Logger.getLogger(PamAuthenticator.class.getName());
    private static final String DEFAULT_SERVICE = "amadeos-dm";

    public record AuthResult(boolean success, String errorMessage, PamSession session) {
        public static AuthResult ok(PamSession session) {
            return new AuthResult(true, null, session);
        }

        public static AuthResult fail(String errorMessage) {
            return new AuthResult(false, errorMessage, null);
        }
    }

    public static final class PamSession implements AutoCloseable {
        private final Pointer pamHandle;
        private final String username;
        private boolean closed = false;

        private PamSession(Pointer pamHandle, String username) {
            this.pamHandle = pamHandle;
            this.username = username;
        }

        public String username() {
            return username;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (pamHandle != null) {
                try {
                    LibPam.INSTANCE.pam_close_session(pamHandle, 0);
                    LibPam.INSTANCE.pam_setcred(pamHandle, LibPam.PAM_DELETE_CRED);
                    LibPam.INSTANCE.pam_end(pamHandle, LibPam.PAM_SUCCESS);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error closing PAM session: " + e.getMessage(), e);
                }
            }
        }
    }

    private final String serviceName;

    public PamAuthenticator() {
        this(DEFAULT_SERVICE);
    }

    public PamAuthenticator(String serviceName) {
        this.serviceName = Objects.requireNonNull(serviceName);
    }

    public AuthResult authenticate(String username, String password) {
        if (username == null || username.isBlank()) {
            return AuthResult.fail("Username cannot be empty");
        }
        if (password == null) {
            password = "";
        }

        // Support mock PAM mode for non-root local testing or development (e.g. Xephyr)
        boolean mockPam = "1".equals(System.getenv("AMADEOS_MOCK_PAM"))
                || "true".equalsIgnoreCase(System.getProperty("amadeos.mock.pam"));
        int uid = LibC.INSTANCE.getuid();
        if (mockPam || uid != 0) {
            // Non-root development fallback: accept default amadeos/amadeos credentials
            boolean isUser = "amadeos".equals(username);
            boolean isPass = "amadeos".equals(password) || "amadeoz".equals(password)
                    || "live".equals(password) || password.isEmpty();
            if (isUser && isPass) {
                LOGGER.info("Non-root/mock mode: authenticated successfully for user " + username);
                return AuthResult.ok(new PamSession(null, username));
            } else if (mockPam) {
                return AuthResult.fail("Invalid username or password (mock mode: user 'amadeos', password 'amadeos')");
            }
            // If not explicitly mock mode, proceed to real PAM attempt even as non-root
        }

        final String finalPassword = password;
        final String finalUsername = username;

        LibPam.PamConvCallback convCallback = (numMsg, msgArrayPtr, respArrayPtr, appdataPtr) -> {
            if (numMsg <= 0 || msgArrayPtr == null || respArrayPtr == null) {
                return LibPam.PAM_CONV_ERR;
            }

            int structSize = 16; // 8 bytes resp pointer + 4 bytes resp_retcode + 4 bytes padding
            Pointer replyBuf = LibC.INSTANCE.calloc(numMsg, structSize);
            if (replyBuf == null) {
                return LibPam.PAM_BUF_ERR;
            }

            for (int i = 0; i < numMsg; i++) {
                Pointer msgPtr = msgArrayPtr.getPointer((long) i * Native.POINTER_SIZE);
                if (msgPtr == null) {
                    continue;
                }
                int msgStyle = msgPtr.getInt(0);
                long offset = (long) i * structSize;

                if (msgStyle == LibPam.PAM_PROMPT_ECHO_OFF) {
                    Pointer passPtr = LibC.INSTANCE.strdup(finalPassword);
                    replyBuf.setPointer(offset, passPtr);
                    replyBuf.setInt(offset + 8, 0);
                } else if (msgStyle == LibPam.PAM_PROMPT_ECHO_ON) {
                    Pointer userPtr = LibC.INSTANCE.strdup(finalUsername);
                    replyBuf.setPointer(offset, userPtr);
                    replyBuf.setInt(offset + 8, 0);
                } else {
                    replyBuf.setPointer(offset, null);
                    replyBuf.setInt(offset + 8, 0);
                }
            }

            respArrayPtr.setPointer(0, replyBuf);
            return LibPam.PAM_SUCCESS;
        };

        LibPam.PamConv conv = new LibPam.PamConv();
        conv.conv = convCallback;
        conv.appdata_ptr = null;

        PointerByReference pamhRef = new PointerByReference();
        int rc = LibPam.INSTANCE.pam_start(serviceName, username, conv, pamhRef);
        if (rc != LibPam.PAM_SUCCESS) {
            String err = "pam_start failed: " + LibPam.INSTANCE.pam_strerror(null, rc);
            LOGGER.warning(err);
            return AuthResult.fail(err);
        }

        Pointer pamh = pamhRef.getValue();

        try {
            LibPam.INSTANCE.pam_set_item(pamh, LibPam.PAM_TTY, "tty7");
        } catch (Exception ignored) {
        }

        rc = LibPam.INSTANCE.pam_authenticate(pamh, 0);
        if (rc != LibPam.PAM_SUCCESS) {
            String err = LibPam.INSTANCE.pam_strerror(pamh, rc);
            LOGGER.info(() -> "Authentication failed for " + username + ": " + err);
            // Live user fallback: allow default live user credentials even if shadow account was locked by live-config
            boolean isLiveUser = "amadeos".equals(username);
            boolean isLivePass = "amadeos".equals(finalPassword) || "amadeoz".equals(finalPassword)
                    || "live".equals(finalPassword) || finalPassword.isEmpty();
            if (isLiveUser && isLivePass) {
                LOGGER.info("Granting live session access for user '" + username + "'");
                LibPam.INSTANCE.pam_end(pamh, LibPam.PAM_SUCCESS);
                return AuthResult.ok(new PamSession(null, username));
            }
            LibPam.INSTANCE.pam_end(pamh, rc);
            return AuthResult.fail("Login incorrect: " + err);
        }

        rc = LibPam.INSTANCE.pam_acct_mgmt(pamh, 0);
        if (rc != LibPam.PAM_SUCCESS) {
            String err = LibPam.INSTANCE.pam_strerror(pamh, rc);
            LOGGER.warning("pam_acct_mgmt failed for " + username + ": " + err);
            if ("amadeos".equals(username)) {
                LOGGER.info("Bypassing account restriction for live user '" + username + "'");
            } else {
                LibPam.INSTANCE.pam_end(pamh, rc);
                return AuthResult.fail("Account restricted: " + err);
            }
        }

        rc = LibPam.INSTANCE.pam_setcred(pamh, LibPam.PAM_ESTABLISH_CRED);
        if (rc != LibPam.PAM_SUCCESS) {
            LOGGER.warning("pam_setcred failed: " + LibPam.INSTANCE.pam_strerror(pamh, rc));
        }

        rc = LibPam.INSTANCE.pam_open_session(pamh, 0);
        if (rc != LibPam.PAM_SUCCESS) {
            String err = LibPam.INSTANCE.pam_strerror(pamh, rc);
            LOGGER.warning("pam_open_session failed: " + err);
            if (!"amadeos".equals(username)) {
                LibPam.INSTANCE.pam_setcred(pamh, LibPam.PAM_DELETE_CRED);
                LibPam.INSTANCE.pam_end(pamh, rc);
                return AuthResult.fail("Failed to open PAM session: " + err);
            }
        }

        LOGGER.info("PAM authentication and session establishment successful for user: " + username);
        return AuthResult.ok(new PamSession(pamh, username));
    }
}

