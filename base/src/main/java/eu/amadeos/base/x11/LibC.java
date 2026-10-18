package eu.amadeos.base.x11;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

public interface LibC extends Library {
    LibC INSTANCE = Native.load("c", LibC.class);

    Pointer malloc(long size);

    Pointer calloc(long nmemb, long size);

    Pointer strdup(String s);

    void free(Pointer ptr);

    int getuid();

    int setuid(int uid);

    int setgid(int gid);

    int initgroups(String user, int group);

    @com.sun.jna.Structure.FieldOrder({
        "pw_name", "pw_passwd", "pw_uid", "pw_gid", "pw_gecos", "pw_dir", "pw_shell"
    })
    class Passwd extends com.sun.jna.Structure {
        public String pw_name;
        public String pw_passwd;
        public int pw_uid;
        public int pw_gid;
        public String pw_gecos;
        public String pw_dir;
        public String pw_shell;

        public static class ByReference extends Passwd implements com.sun.jna.Structure.ByReference {}
    }

    Passwd.ByReference getpwnam(String name);

    Passwd.ByReference getpwuid(int uid);
}
