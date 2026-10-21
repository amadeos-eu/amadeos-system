# amadeos-system

The system components of [amadeOS](https://github.com/datazuul/amadeos), built together as one Gradle
multi-project: one module and one Debian package per component. The standalone applications
(amade-files, amade-web, amade-pad, ...) keep their own repositories; the
[amadeos](https://github.com/datazuul/amadeos) repository builds the live image from all packages.

![License](https://img.shields.io/badge/license-EUPL--1.2-blue)
![Java](https://img.shields.io/badge/Java-25-orange)

## Modules

| Module | Package | What it is |
|---|---|---|
| `base` | (in every desktop package) | The library under the desktop modules: Xlib, Xss, Xkb, libc and PAM through JNA, the EWMH client helper, launch feedback and the lock selection, XDG directories and `.desktop` files, theme, icons and backdrops, the process and lock launchers, the session dialogs, the network service and the bell. Nothing in it depends on the other modules; they all depend on it |
| [`wm`](wm/README.md) | `amadeos-wm` | The window manager: a floating, reparenting X11 window manager with Motif/CDE frames, four workspaces and the root menu (`amadeos-wm [display]`) |
| [`panel-cde`](panel-cde/README.md) | `amadeos-panel-cde` | The CDE Front Panel: clock, calendar, workspace switch (buttons in the theme's workspace colours), lock and language buttons, network cell, updates cell (shown while updates wait: count, red for security updates, menu with Install all / Install security updates only / Check now) and the drawers of installed applications. Provides `amadeos-panel`, the panel the session starts |
| `dm` | `amadeos-dm` | The display manager: `amadeos-dm.service` starts Xorg and the login greeter, which authenticates through PAM in a root worker and starts the session (`amadeos-dm [display]`; `amadeos-dm greeter` shows the greeter alone) |
| `session` | `amadeos-session` | The X session (`amadeos-session`: window manager under supervision, autostart scripts, then the panel), the screen lock (`amadeos-session lock`, wrapped by `amadeos-lock`) and the screen saver with idle lock (`amadeos-session screen`) |
| `style` | `amadeos-style` | The Style Manager (CDE's dtstyle: Color, Backdrop, Keyboard, Mouse, Beep, Screen) and the input settings tool (`amadeos-style input`, run at login) |
| `logviewer` | `amadeos-logviewer` | Session Diagnostics & Logs: the session, window manager and application logs, the display manager's logs and the journal of this boot, one at a time with Follow; started from the panel's Tools drawer |
| `print` | `amadeos-print` | Print manager: CUPS printers with their state, the jobs of each printer (cancel, hold, release), pause and resume printers, default printer, test page, CUPS administration for adding printers |
| `sysinfo` | `amadeos-sysinfo` | System information: operating system, processor, memory, storage, graphics, network and battery on one page; copy or save as text |
| `packages` | `amadeos-packages` (helper only; the library is in the packages that use it) | Package management for the update cell and the Software program (ROADMAP M15): `PackageService` reads upgrades, installed and available packages and the catalog sorted into categories, and searches, with apt as the user, and installs, upgrades and removes through the root helper `/usr/libexec/amadeos-packages` with progress. The only module that talks to apt |
| `software` | `amadeos-software` | Software: finds, installs, updates and removes software. Tabs Applications, Command line, Other and (with *Show libraries*) Libraries, with one search and an *All / Installed* filter over them; the package's description next to the list with Install, Remove and Update, and a question before anything else gets installed or removed with it. The Updates tab appears while updates wait (security updates first, *Install selected* or *Security updates only*); the front panel's updates cell opens it (`--updates`, `--settings`). A progress bar with the apt log, a restart bar when an update needs one, and the setting for automatic security updates. The tabs come from AppStream data where apt has fetched it (package `appstream`), the files of installed packages, and else the Debian section |

The desktop code came from the amadeos repository with its history (`git filter-repo`); that
repository keeps the live image and its configuration. A module's `packaging/root` is a file system
tree that goes into its package (files under `etc/` as conffiles), `packaging/debian` holds its
maintainer scripts.

## The desktop

The desktop is the five packages `amadeos-dm`, `amadeos-session`, `amadeos-wm`, `amadeos-panel-cde` and
`amadeos-style`, each a jar under `/opt/<package>/` with the launcher `/usr/bin/<package>`; the programs
of the Tools drawer (`amadeos-logviewer`, `amadeos-sysinfo`) and `amadeos-print` are packages of their own
that the panel starts. `amadeos-dm`
depends on `amadeos-session`, which depends on `amadeos-wm` and on `amadeos-panel`. `amadeos-panel` is a virtual
package: `amadeos-panel-cde` provides it and registers `/usr/bin/amadeos-panel` as an alternative, and
`amadeos-session` starts that. A second panel (say a taskbar) provides `amadeos-panel` the same way and
takes the Front Panel's place when installed instead; with both installed, `update-alternatives` or
`AUTOSTART_APP` in `session.conf` chooses. The window manager treats any window of type
`_NET_WM_WINDOW_TYPE_DOCK` as the panel and knows none by name.

| Component | Package | What it does |
|---|---|---|
| **Display manager** | `amadeos-dm` | Started by `amadeos-dm.service` once Xorg runs on VT 7. Shows the login greeter (language and time zone of the session, power actions), authenticates through PAM (`/etc/pam.d/amadeos-dm`) in a root worker process, opens a logind session and starts `amadeos-session` as the user. As root it points `/etc/localtime` at the chosen zone and writes `/etc/timezone`. |
| **Session** | `amadeos-session` | `/usr/bin/amadeos-session`: the window manager under supervision, the scripts of `/etc/amadeos/autostart.d` and `~/.config/amadeos/autostart.d`, then the panel. Settings in `/etc/amadeos/session.conf`, overridden by `~/.config/amadeos/session.conf`. Registered as `x-session-manager`, so `startx` without the display manager starts the desktop too. |
| **Window manager** | `amadeos-wm` | A floating, reparenting X11 window manager (Xlib via JNA) with Motif/CDE-style frames, interactive move and resize, minimize to desktop icons, four virtual workspaces, a desktop root menu and the EWMH/ICCCM hints that panels and toolkits rely on (`_NET_CLIENT_LIST`, `_NET_ACTIVE_WINDOW`, `_NET_CURRENT_DESKTOP`, `_NET_WM_STRUT_PARTIAL`, `WM_DELETE_WINDOW`, ...). |
| **Front panel** | `amadeos-panel-cde` | A CDE-style dock with clock, calendar, workspace switcher, lock and language buttons, the network cell and drawers that list the installed applications from their `.desktop` files. The Tools drawer holds the terminals, the Style Manager, help and the log viewer. Programs started from the panel log to `~/.cache/amadeos/logs/<program>.log`. |
| **Style Manager** | `amadeos-style` | The controls of CDE's dtstyle. Color (palette with its four workspace colours, and the icon theme), Backdrop, Keyboard, Mouse, Beep and Screen work; Font, Window and Startup are shown but not yet available. Writes `~/.config/amadeos/theme.conf`, which the window manager, the panel and every amade application follow. `amadeos-style input` applies keyboard repeat and click, mouse handedness, double-click time and acceleration, and the bell from `~/.config/amadeos/input.conf` with `xset`, `xmodmap` and `xrdb`; run at login (`/etc/amadeos/autostart.d/10-amadeos-input`) and on Apply in the Style Manager. |
| **Screen saver** | `amadeos-session` | CDE's dtscreen: uses the X server's idle counter (MIT-SCREEN-SAVER, `libxss1`), shows a saver module (Qix, Swarm, Starfield, Floating Logo, Blank) and locks the screen after the configured time. Started by `/etc/amadeos/autostart.d/50-amadeos-screen`; settings in `~/.config/amadeos/screen.conf`. |
| **Screen lock** | `amadeos-session` | Grabs keyboard and mouse and asks for the user's password (PAM, `/etc/pam.d/amadeos-lock`). `/usr/bin/amadeos-lock` is the wrapper that locks again if the lock process dies; the window manager (Super+L), the panel's lock button and the screen saver start it. |

Language: English, Deutsch, Français, Español, Italiano, chosen in the greeter or with the panel's
language button; both write `~/.config/amadeos/locale.conf`. The panel's texts are in
`panel-cde/src/main/resources/eu/amadeos/system/i18n/desktop*.properties`.

What the packages install besides their jars and launchers (`packaging/root` of a module mirrors the
file system; the files under `etc/` are conffiles):

| Path | Package | What it is |
|---|---|---|
| `/usr/bin/amadeos-dm` | `amadeos-dm` | Starts Xorg on VT 7 with an authorization cookie and the display manager; applies the keyboard layout of `/etc/default/keyboard`. |
| `/usr/lib/systemd/system/amadeos-dm.service` | `amadeos-dm` | The unit around it, alias `display-manager.service`; the postinst enables it and sets `graphical.target` as the default target. |
| `/etc/pam.d/amadeos-dm` | `amadeos-dm` | The PAM service of the greeter. |
| `/usr/bin/amadeos-session`, `/etc/amadeos/session.conf` | `amadeos-session` | The session script and its settings (`WM_COMMAND`, `AUTOSTART_APP`, logging). |
| `/etc/amadeos/autostart.d/` | `amadeos-session`, `amadeos-style` | Run at the start of every session: `10-amadeos-input` (input settings, from `amadeos-style`), `50-amadeos-screen` (screen saver). Users add their own in `~/.config/amadeos/autostart.d`. |
| `/usr/bin/amadeos-lock`, `/etc/pam.d/amadeos-lock` | `amadeos-session` | The screen lock wrapper and the PAM service of the lock. |
| `/usr/share/polkit-1/rules.d/50-amadeos-network.rules` | `amadeos-panel-cde` | Members of `netdev` manage NetworkManager connections without a password (network cell of the panel). |
| `/usr/libexec/amadeos-packages` | `amadeos-packages` | The root helper of package management (`refresh`, `upgrade [--security-only] [PKG…]`, `install`, `remove`, `check`, `automatic-security on`/`off`), a shell script run through `pkexec`. Progress on standard output (apt's `APT::Status-Fd` lines and `@@done`, `@@error ID MESSAGE`, `@@reboot-required`), apt's messages on standard error and in `/var/log/amadeos-packages.log`. A busy dpkg lock ends it at once with exit code 3. |
| `/etc/apt/apt.conf.d/50amadeos-unattended` | written by `amadeos-packages automatic-security` | Turns the daily unattended-upgrades run (security updates only) on or off; `on` installs `unattended-upgrades` first. Absent until the setting is changed in Software. |
| `/usr/share/polkit-1/actions/eu.amadeos.packages.policy`, `/usr/share/polkit-1/rules.d/50-amadeos-packages.rules` | `amadeos-packages` | Installing, upgrading and removing asks for the administrator's password (kept for a few minutes); refreshing the package lists does not. |
| `x-session-manager`, `amadeos-panel` | `amadeos-session`, `amadeos-panel-cde` | Alternatives registered by the postinst scripts. |

What belongs to the live image alone (the live user's password, `/etc/X11/Xwrapper.config`, live-config)
stays in the [amadeos](https://github.com/datazuul/amadeos) repository.

## Building

```sh
./gradlew build                 # all modules, with tests
./gradlew deb                   # build/distributions/amadeos-<module>_<version>_all.deb per module
./gradlew :print:installToAmadeOs   # one package into ../amadeos/config/packages.chroot/
./gradlew :sysinfo:run          # start a module from the build
./gradlew :panel-cde:run        # the front panel on your current desktop
./gradlew :style:run            # the Style Manager
wm/run-xephyr.sh                # window manager and panel in a nested X server
dm/run-greeter.sh               # the login greeter in a nested X server (mock PAM, user amadeos / amadeos)
```

Java 25 and `dpkg-deb` (for the packages) are needed. The X11 integration tests of the window manager
and the lock start their own Xvfb and use `xmessage` as a test client (`xvfb`, `x11-utils`); on a
machine without a display run the tests under `xvfb-run -a`. One test of `base` is skipped unless the
`amadeos-backdrops-cde` package is installed. With
[amade-commons](https://github.com/datazuul/amade-commons) checked out next to this repository, the build
uses that checkout (composite build); otherwise the version from JitPack named in `build.gradle`.

## amadeos-print and CUPS

amadeos-print talks IPP (RFC 8010/8011) to CUPS itself, without `lpstat` or `cancel`: over the CUPS socket
`/run/cups/cups.sock`, or `http://localhost:631` when there is no socket. On the socket CUPS asks who is
calling and amadeos-print answers with `PeerCred`, as libcups does; CUPS checks that against the socket's
peer credentials. So the owner of a job can cancel it, and members of the group `lpadmin` can pause,
resume and choose the default printer, without a password.

Printers are added in the CUPS web interface (*Printer > Add or Configure Printers…*); driverless network
and USB printers (IPP Everywhere, AirPrint) usually appear by themselves. The test page is printed through
the JDK's print service.

## License

[EUPL-1.2](LICENSE)
