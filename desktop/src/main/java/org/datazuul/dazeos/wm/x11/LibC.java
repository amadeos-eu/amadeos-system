package org.datazuul.dazeos.wm.x11;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

public interface LibC extends Library {
    LibC INSTANCE = Native.load("c", LibC.class);

    Pointer malloc(long size);

    void free(Pointer ptr);
}
