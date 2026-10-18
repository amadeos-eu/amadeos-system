package eu.amadeos.system.base.pam;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.PointerByReference;

public interface LibPam extends Library {
    LibPam INSTANCE = Native.load("pam", LibPam.class);

    int PAM_SUCCESS = 0;
    int PAM_OPEN_ERR = 1;
    int PAM_SYMBOL_ERR = 2;
    int PAM_SERVICE_ERR = 3;
    int PAM_SYSTEM_ERR = 4;
    int PAM_BUF_ERR = 5;
    int PAM_PERM_DENIED = 6;
    int PAM_AUTH_ERR = 7;
    int PAM_CRED_INSUFFICIENT = 8;
    int PAM_AUTHINFO_UNAVAIL = 9;
    int PAM_USER_UNKNOWN = 10;
    int PAM_MAXTRIES = 11;
    int PAM_NEW_AUTHTOK_REQD = 12;
    int PAM_ACCT_EXPIRED = 13;
    int PAM_SESSION_ERR = 14;
    int PAM_CRED_UNAVAIL = 15;
    int PAM_CRED_EXPIRED = 16;
    int PAM_CRED_ERR = 17;
    int PAM_NO_MODULE_DATA = 18;
    int PAM_CONV_ERR = 19;
    int PAM_AUTHTOK_ERR = 20;
    int PAM_AUTHTOK_RECOVERY_ERR = 21;
    int PAM_AUTHTOK_LOCK_BUSY = 22;
    int PAM_AUTHTOK_DISABLE_AGING = 23;
    int PAM_TRY_AGAIN = 24;
    int PAM_IGNORE = 25;
    int PAM_ABORT = 26;
    int PAM_AUTHTOK_EXPIRED = 27;
    int PAM_MODULE_UNKNOWN = 28;
    int PAM_BAD_ITEM = 29;
    int PAM_CONV_AGAIN = 30;
    int PAM_INCOMPLETE = 31;

    int PAM_PROMPT_ECHO_OFF = 1;
    int PAM_PROMPT_ECHO_ON = 2;
    int PAM_ERROR_MSG = 3;
    int PAM_TEXT_INFO = 4;

    int PAM_ESTABLISH_CRED = 0x0002;
    int PAM_DELETE_CRED = 0x0004;
    int PAM_REINITIALIZE_CRED = 0x0008;
    int PAM_REFRESH_CRED = 0x0010;

    int PAM_SILENT = 0x8000;

    int PAM_SERVICE = 1;
    int PAM_USER = 2;
    int PAM_TTY = 3;
    int PAM_RHOST = 4;
    int PAM_CONV = 5;
    int PAM_AUTHTOK = 6;
    int PAM_OLDAUTHTOK = 7;
    int PAM_RUSER = 8;
    int PAM_USER_PROMPT = 9;

    interface PamConvCallback extends Callback {
        int invoke(int numMsg, Pointer msgArrayPtr, Pointer respArrayPtr, Pointer appdataPtr);
    }

    @Structure.FieldOrder({"conv", "appdata_ptr"})
    class PamConv extends Structure {
        public PamConvCallback conv;
        public Pointer appdata_ptr;

        public static class ByReference extends PamConv implements Structure.ByReference {}
    }

    int pam_start(String serviceName, String user, PamConv conv, PointerByReference pamh);
    int pam_end(Pointer pamh, int pamStatus);
    int pam_authenticate(Pointer pamh, int flags);
    int pam_setcred(Pointer pamh, int flags);
    int pam_acct_mgmt(Pointer pamh, int flags);
    int pam_open_session(Pointer pamh, int flags);
    int pam_close_session(Pointer pamh, int flags);
    int pam_set_item(Pointer pamh, int itemType, Pointer item);
    int pam_set_item(Pointer pamh, int itemType, String item);
    int pam_putenv(Pointer pamh, String nameValue);
    /** Returns a NULL-terminated, malloc'ed array of malloc'ed "NAME=value" strings; the caller frees both. */
    Pointer pam_getenvlist(Pointer pamh);
    String pam_strerror(Pointer pamh, int errnum);
}

