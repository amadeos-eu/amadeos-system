package eu.amadeos.system.base.pam;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import eu.amadeos.system.base.x11.LibC;

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
        // PAM keeps a pointer to the conversation; keep it reachable until pam_end.
        @SuppressWarnings("unused")
        private final LibPam.PamConv conversation;
        private boolean closed = false;

        private PamSession(Pointer pamHandle, String username) {
            this(pamHandle, username, null);
        }

        private PamSession(Pointer pamHandle, String username, LibPam.PamConv conversation) {
            this.pamHandle = pamHandle;
            this.username = username;
            this.conversation = conversation;
        }

        public String username() {
            return username;
        }

        /**
         * The PAM environment after the session was opened (e.g. XDG_RUNTIME_DIR, XDG_SESSION_ID
         * from pam_systemd, variables from pam_env). Empty for mock sessions.
         */
        public synchronized Map<String, String> environment() {
            Map<String, String> result = new LinkedHashMap<>();
            if (pamHandle == null || closed) {
                return result;
            }
            Pointer list = LibPam.INSTANCE.pam_getenvlist(pamHandle);
            if (list == null) {
                return result;
            }
            try {
                for (int i = 0; ; i++) {
                    Pointer entry = list.getPointer((long) i * Native.POINTER_SIZE);
                    if (entry == null) {
                        break;
                    }
                    String pair = entry.getString(0);
                    LibC.INSTANCE.free(entry);
                    int eq = pair.indexOf('=');
                    if (eq > 0) {
                        result.put(pair.substring(0, eq), pair.substring(eq + 1));
                    }
                }
            } finally {
                LibC.INSTANCE.free(list);
            }
            return result;
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

    /**
     * Authenticates the user, checks the account and opens a PAM session for a login.
     * The caller must close the returned session when the login session ends.
     */
    public AuthResult authenticate(String username, String password) {
        return run(username, password, true, "tty7", Map.of());
    }

    /**
     * Like {@link #authenticate} for a graphical login: sets PAM_TTY (the X display, e.g. ":0")
     * and puts the given variables into the PAM environment before authenticating, so modules
     * such as pam_systemd see XDG_SEAT, XDG_VTNR and XDG_SESSION_TYPE and register an active
     * graphical seat session.
     */
    public AuthResult openLoginSession(String username, String password, String tty, Map<String, String> environment) {
        return run(username, password, true, tty, environment);
    }

    /**
     * Only checks the password and account, without establishing credentials or a session.
     * Used to unlock a screen of a session that is already open.
     */
    public AuthResult verify(String username, String password) {
        AuthResult result = run(username, password, false, "tty7", Map.of());
        if (result.success() && result.session() != null) {
            result.session().close();
        }
        return result;
    }

    private AuthResult run(String username, String password, boolean openSession,
                           String tty, Map<String, String> environment) {
        if (username == null || username.isBlank()) {
            return AuthResult.fail("Username cannot be empty");
        }
        if (password == null) {
            password = "";
        }

        if (mockModeRequested()) {
            if (LibC.INSTANCE.getuid() == 0) {
                // Never let a development switch weaken authentication of the real display manager.
                LOGGER.severe("Ignoring mock PAM mode: it is only available when not running as root");
            } else {
                return mockAuthenticate(username, password);
            }
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
            LibPam.INSTANCE.pam_set_item(pamh, LibPam.PAM_TTY, tty);
        } catch (Exception ignored) {
        }
        for (Map.Entry<String, String> variable : environment.entrySet()) {
            LibPam.INSTANCE.pam_putenv(pamh, variable.getKey() + "=" + variable.getValue());
        }

        rc = LibPam.INSTANCE.pam_authenticate(pamh, 0);
        if (rc != LibPam.PAM_SUCCESS) {
            String err = LibPam.INSTANCE.pam_strerror(pamh, rc);
            LOGGER.info(() -> "Authentication failed: " + err);
            LibPam.INSTANCE.pam_end(pamh, rc);
            return AuthResult.fail("Login incorrect");
        }

        rc = LibPam.INSTANCE.pam_acct_mgmt(pamh, 0);
        if (rc != LibPam.PAM_SUCCESS) {
            String err = LibPam.INSTANCE.pam_strerror(pamh, rc);
            LOGGER.warning("pam_acct_mgmt failed for " + username + ": " + err);
            LibPam.INSTANCE.pam_end(pamh, rc);
            return AuthResult.fail("Account restricted: " + err);
        }

        if (!openSession) {
            LibPam.INSTANCE.pam_end(pamh, LibPam.PAM_SUCCESS);
            return AuthResult.ok(null);
        }

        rc = LibPam.INSTANCE.pam_setcred(pamh, LibPam.PAM_ESTABLISH_CRED);
        if (rc != LibPam.PAM_SUCCESS) {
            String err = LibPam.INSTANCE.pam_strerror(pamh, rc);
            LOGGER.warning("pam_setcred failed for " + username + ": " + err);
            LibPam.INSTANCE.pam_end(pamh, rc);
            return AuthResult.fail("Failed to establish credentials: " + err);
        }

        rc = LibPam.INSTANCE.pam_open_session(pamh, 0);
        if (rc != LibPam.PAM_SUCCESS) {
            String err = LibPam.INSTANCE.pam_strerror(pamh, rc);
            LOGGER.warning("pam_open_session failed for " + username + ": " + err);
            LibPam.INSTANCE.pam_setcred(pamh, LibPam.PAM_DELETE_CRED);
            LibPam.INSTANCE.pam_end(pamh, rc);
            return AuthResult.fail("Failed to open PAM session: " + err);
        }

        LOGGER.info("PAM authentication and session establishment successful for user: " + username);
        return AuthResult.ok(new PamSession(pamh, username, conv));
    }

    private static boolean mockModeRequested() {
        return "1".equals(System.getenv("AMADEOS_MOCK_PAM"))
                || "true".equalsIgnoreCase(System.getProperty("amadeos.mock.pam"));
    }

    /**
     * Development-only stand-in for PAM (e.g. the greeter in Xephyr). Accepts the default
     * live credentials, including "amadeoz" for a German keyboard typed as US layout.
     */
    private static AuthResult mockAuthenticate(String username, String password) {
        if ("amadeos".equals(username) && ("amadeos".equals(password) || "amadeoz".equals(password))) {
            LOGGER.info("Mock PAM mode: authenticated user " + username);
            return AuthResult.ok(new PamSession(null, username));
        }
        return AuthResult.fail("Invalid username or password (mock mode: user 'amadeos', password 'amadeos')");
    }
}

