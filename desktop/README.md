# amade-desktop

The desktop of amadeOS: window manager, front panel, display manager with login greeter, screen lock,
screen saver, Style Manager and the input settings tool. All of it is one jar, started through
`/usr/bin/amade-desktop <component>`; the first argument selects the component
(`AmadeOsLauncher`).

| Component | Argument | What it does |
|---|---|---|
| **Display manager** | `dm` | Started by `amadeos-dm.service` once Xorg runs on VT 7. Shows the login greeter (language and time zone of the session, power actions), authenticates through PAM (`/etc/pam.d/amadeos-dm`) in a root worker process (`session-worker`), opens a logind session and starts `amadeos-session` as the user. As root it points `/etc/localtime` at the chosen zone and writes `/etc/timezone`. |
| **Window manager** | `wm` | A floating, reparenting X11 window manager (Xlib via JNA) with Motif/CDE-style frames, interactive move and resize, minimize to desktop icons, four virtual workspaces, a desktop root menu and the EWMH/ICCCM hints that panels and toolkits rely on (`_NET_CLIENT_LIST`, `_NET_ACTIVE_WINDOW`, `_NET_CURRENT_DESKTOP`, `_NET_WM_STRUT_PARTIAL`, `WM_DELETE_WINDOW`, ...). |
| **Front panel** | `panel` (default) | A CDE-style dock with clock, calendar, workspace switcher, lock and language buttons, the network cell and drawers that list the installed applications from their `.desktop` files. The Tools drawer holds the terminals, the Style Manager, help and the log viewer. Programs started from the panel log to `~/.cache/amadeos/logs/<program>.log`. |
| **Style Manager** | `style` | The controls of CDE's dtstyle. Color (palette and icon theme), Backdrop, Keyboard, Mouse, Beep and Screen work; Font, Window and Startup are shown but not yet available. Writes `~/.config/amadeos/theme.conf`, which the window manager, the panel and every amade application follow. |
| **Input settings** | `input` | Applies keyboard repeat and click, mouse handedness, double-click time and acceleration, and the bell from `~/.config/amadeos/input.conf` with `xset`, `xmodmap` and `xrdb`; run at login (`/etc/amadeos/autostart.d/10-amadeos-input`) and on Apply in the Style Manager. |
| **Screen saver** | `screen` | CDE's dtscreen: uses the X server's idle counter (MIT-SCREEN-SAVER, `libxss1`), shows a saver module (Qix, Swarm, Starfield, Floating Logo, Blank) and locks the screen after the configured time. Started by `/etc/amadeos/autostart.d/50-amadeos-screen`; settings in `~/.config/amadeos/screen.conf`. |
| **Screen lock** | `lock` | Grabs keyboard and mouse and asks for the user's password (PAM). `/usr/bin/amadeos-lock` is the wrapper that locks again if the lock process dies; the panel's lock button and the screen saver start it. |

Language: English, Deutsch, Français, Español, Italiano, chosen in the greeter or with the panel's
language button; both write `~/.config/amadeos/locale.conf`. The texts are in
`src/main/resources/org/datazuul/amadeos/i18n/desktop*.properties`.

## What the package installs

`packaging/root` mirrors the file system and goes into the package as it is; the files under `etc/`
are conffiles. `packaging/debian` holds the maintainer scripts.

| Path | What it is |
|---|---|
| `/opt/amade-desktop/amade-desktop.jar`, `/usr/bin/amade-desktop` | The jar and its launcher (`amade-desktop <component>`). |
| `/usr/bin/amadeos-dm` | Starts Xorg on VT 7 with an authorization cookie and the display manager; applies the keyboard layout of `/etc/default/keyboard`. |
| `/usr/lib/systemd/system/amadeos-dm.service` | The unit around it, alias `display-manager.service`; the package's postinst enables it and sets `graphical.target` as the default target. |
| `/etc/pam.d/amadeos-dm` | The PAM service of the greeter and the screen lock. |
| `/usr/bin/amadeos-session` | The session of a logged-in user: window manager under supervision, the autostart scripts, then the front panel. Settings in `/etc/amadeos/session.conf`, overridden by `~/.config/amadeos/session.conf`. |
| `/etc/amadeos/autostart.d/` | Run at the start of every session: `10-amadeos-input` (input settings), `50-amadeos-screen` (screen saver). Users add their own in `~/.config/amadeos/autostart.d`. |
| `/usr/bin/amadeos-lock` | The screen lock wrapper, locking again if the lock process dies. |
| `/usr/share/polkit-1/rules.d/50-amadeos-network.rules` | Members of `netdev` manage NetworkManager connections without a password (network cell of the panel). |
| `x-session-manager` | The postinst registers `amadeos-session` as an alternative, so `startx` without the display manager (Xsession) starts the desktop too. |

What belongs to the live image alone (the live user's password, `/etc/X11/Xwrapper.config`, live-config)
stays in the [amadeos](https://github.com/datazuul/amadeos) repository.

## Developing

```sh
../gradlew :desktop:test        # unit and X11 integration tests (the latter start their own Xvfb and need xmessage)
../gradlew :desktop:runPanel    # the front panel on your current desktop
./run-xephyr.sh                 # window manager and panel in a nested X server
./run-greeter.sh                # the login greeter in a nested X server (mock PAM, user amadeos / amadeos)
```

Run the whole test task under `xvfb-run -a` on a machine without a display. One test is skipped unless
the `amadeos-backdrops-cde` package is installed.

### Front Panel slots and widgets

The Front Panel layout is defined in code, in the constructor of `AmadeDesktopPanel`
(`src/main/java/org/datazuul/amadeos/wm/panel/`). Slots are added left to right to `leftSlots` (left
of the workspace switcher) or `rightSlots` (right of it), and every widget is also added to `widgets`,
so it is started and follows theme changes:

- **Drawer filled from an XDG category:** `createCategoryDrawer(id, title, iconKey, XdgCategory.X, fallbackAction, defaultTools, true)`. Example: the Sound & Video slot uses `XdgCategory.AUDIO_VIDEO`.
- **Plain launcher:** `new DrawerDockWidget(id, tooltip, iconKey, action, List.of(), false)`.
- **Live widget** (like Clock or Calendar): a class in `panel/widget/` extending `AbstractDockWidget`. Paint the slot with `CdePanelChrome.paintSlotBay(...)`, set the preferred size to `CdePanelChrome.SLOT_WIDTH × SLOT_HEIGHT`, leave `TAB_HEIGHT + 1` free at the top for the drawer notch, and react to colour changes in `updateThemeStyles(theme)`.

The dock width follows the slots' preferred sizes, so no constant needs changing when a slot is added.
Update the slot list in the Help dialog ("Front Panel Overview") as well.

### Icons

Panel and desktop icons are looked up by **icon key** in `IconManager`
(`src/main/java/org/datazuul/amadeos/wm/theme/`). Each key maps to a list of Freedesktop icon names
(`FREEDESKTOP_NAMES`). The current icon theme (default: NsCDE) is searched for each name in turn, then
its inherited themes and `hicolor`; the first match wins. If nothing is found, a simple icon is drawn in
code (`generatePixmaps`).

Because every name is tried in the current theme before falling back to `hicolor`, an icon installed to
`hicolor` does not win against a later name that NsCDE has. amadeOS's own icons (`amadeos-<name>`) are
therefore added to the NsCDE theme package, which the amadeos repository builds
(`icons/`, `scripts/package-nscde-icons.sh`; see its CONTRIBUTING.md). Put `amadeos-<name>` first in the
key's list in `FREEDESKTOP_NAMES`, keeping the standard names after it as a fallback for other icon themes:

```java
map.put("multimedia", List.of("amadeos-multimedia", "applications-multimedia", "audio-x-generic"));
```

The default wallpaper `src/main/resources/wallpapers/default.png` is rendered from
`icons/src/wallpaper.svg` in the amadeos repository, like the copy in its `config/includes.chroot`.
