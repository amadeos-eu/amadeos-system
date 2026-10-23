# amadeos-wm

The window manager of amadeOS: a floating, reparenting X11 window manager (Xlib via JNA) with
Motif/CDE-style frames, interactive move and resize, minimize to desktop icons, four virtual workspaces,
a desktop root menu and the EWMH/ICCCM hints that panels and toolkits rely on (`_NET_CLIENT_LIST`,
`_NET_ACTIVE_WINDOW`, `_NET_CURRENT_DESKTOP`, `_NET_WM_STRUT_PARTIAL`, `WM_DELETE_WINDOW`, ...).
`/usr/bin/amadeos-wm [display]`; `amadeos-session` (package amadeos-session) starts it under supervision.

The window manager knows no panel by name: a window of type `_NET_WM_WINDOW_TYPE_DOCK` is the panel,
kept above the others, outside the frames, with its strut taken from the usable screen. Super+L and the
root menu start the screen lock through `/usr/bin/amadeos-lock` and the Settings through
`/usr/bin/amadeos-settings`; the session dialogs (logout, power) are in the module `base`, shared with the
panel.

## Developing

```sh
../gradlew :wm:test             # unit and X11 integration tests (the latter start their own Xvfb and need xmessage)
./run-xephyr.sh                 # window manager and panel in a nested X server
```

Run the test task under `xvfb-run -a` on a machine without a display. `run-xephyr.sh` builds the
window manager, panel and session jars and puts the session jar on the window manager's class path, so
Super+L locks the screen without the installed wrapper.
