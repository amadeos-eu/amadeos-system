# amadeos-panel-cde

The CDE Front Panel of amadeOS: a dock with clock, calendar, workspace switcher, lock and language

buttons, the network, sound and updates cells and drawers that list the installed applications from their `.desktop` files.
The Games drawer lists the entries of category `Game` (the amade-*-game packages). The Text & Office drawer lists the `TextEditor` and `WordProcessor` entries, then the other `Office` ones (Calendar, spreadsheets, presentations), as Mail & Internet lists `Email` and then `Network`. The Tools drawer holds
the terminals, the Style Manager and the log viewer. The Help drawer opens the amadeOS User's Handbook
(`/usr/share/doc/amadeos-handbook/html/index.html` from the package `amadeos-handbook`, else the copy on
the web) with `xdg-open`, so in the web browser, and then lists the `Documentation` entries. Programs started from

The workspace switch in the middle follows CDE's palettes: each of the four buttons "One" to "Four" is
filled with its workspace's colour from the theme (`DesktopTheme.workspaceColor(i)`; blue-grey, dialog
colour, teal and rose in Warm Motif), its label in the Motif foreground of that colour, and the current
workspace is the button pressed in. Custom themes set `workspace1` to `workspace4` in `theme.properties`.

The package provides `amadeos-panel` and registers `/usr/bin/amadeos-panel-cde` as the alternative
`/usr/bin/amadeos-panel`, which `amadeos-session` starts. Another panel package provides `amadeos-panel` the
same way; the window manager treats any `_NET_WM_WINDOW_TYPE_DOCK` window as the panel. The panel talks
to the window manager only through X11: the EWMH properties on the root window, the `_AMADEOS_THEME`
atom and the `_AMADEOS_LAUNCH` message (busy cursor while a program starts).

The status slot on the right holds up to four cells. The network cell is always there. Next to it, the
sound cell shows a loudspeaker with one to three waves for the volume, crossed out when muted. Its menu
has a volume slider with a mute button, the outputs and the inputs as radio items and *Mute microphone*;
choosing a device also moves the sounds playing or the recordings running to it. The mouse wheel over the
cell changes the volume in 5 % steps and the middle button mutes. It works through `pactl`
(`PactlSoundService` in base, which reaches PipeWire through pipewire-pulse) and follows `pactl subscribe`,
so changes made by other programs show at once. The updates cell
appears only while software updates wait: an arrow into a tray with their number, on red when security
updates are among them; the tooltip says "12 updates, 3 security". Its menu lists the first updates
(security first) and offers *Install all updates*, *Install security updates only* and *Check now*;
*Show updates…* and *Settings…* open the Software program (`amadeos-software --updates` / `--settings`) and
appear once it is installed.
The cell reads the pending updates through `PackageService` (module `packages`) every 30 minutes and when
`/var/lib/dpkg/status` or `/var/lib/apt/lists` change, so it needs no privileges and causes no downloads;
installing and *Check now* go through `pkexec /usr/libexec/amadeos-packages` (package `amadeos-packages`).

Language: English, Deutsch, Français, Español, Italiano, chosen in the greeter or with the panel's
language button; both write `~/.config/amadeos/locale.conf`. The texts are in
`src/main/resources/eu/amadeos/system/i18n/desktop*.properties`.

## Developing

```sh
../gradlew :panel-cde:test      # unit and rendering tests (under xvfb-run -a without a display)
../gradlew :panel-cde:run       # the front panel on your current desktop
../wm/run-xephyr.sh             # window manager and panel in a nested X server
```

### Front Panel slots and widgets

The Front Panel layout is defined in code, in the constructor of `AmadeDesktopPanel`
(`src/main/java/eu/amadeos/system/panel/cde/`). Slots are added left to right to `leftSlots` (left
of the workspace switcher) or `rightSlots` (right of it), and every widget is also added to `widgets`,
so it is started and follows theme changes:

- **Drawer filled from an XDG category:** `createCategoryDrawer(id, title, iconKey, XdgCategory.X, fallbackAction, defaultTools, true)`. Example: the Sound & Video slot uses `XdgCategory.AUDIO_VIDEO`.
- **Plain launcher:** `new DrawerDockWidget(id, tooltip, iconKey, action, List.of(), false)`.
- **Live widget** (like Clock or Calendar): a class in `panel/cde/widget/` extending `AbstractDockWidget`. Paint the slot with `CdePanelChrome.paintSlotBay(...)`, set the preferred size to `CdePanelChrome.SLOT_WIDTH × SLOT_HEIGHT`, leave `TAB_HEIGHT + 1` free at the top for the drawer notch, and react to colour changes in `updateThemeStyles(theme)`.

A `SlotSpacerDockWidget` (a 10px empty bay) sits between the last slot of each side and the switch, as in CDE.
The dock width follows the slots' preferred sizes, so no constant needs changing when a slot is added.
Update the slot list in the Help dialog ("Front Panel Overview") as well.

### Icons

Panel and desktop icons are looked up by **icon key** in `IconManager`
(`../base/src/main/java/eu/amadeos/system/base/theme/`). Each key maps to a list of Freedesktop icon names
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
