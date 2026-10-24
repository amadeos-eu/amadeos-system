package eu.amadeos.system.wm.switcher;

import java.awt.Image;

/**
 * One window in the Alt+Tab switcher.
 *
 * @param clientWin the client window
 * @param title     its title
 * @param preview   the last picture of the window as it looked on screen, or null if it was never seen whole
 * @param icon      the application's icon (_NET_WM_ICON), or null
 * @param minimized whether the window is minimized
 */
public record SwitcherEntry(long clientWin, String title, Image preview, Image icon, boolean minimized) {
}
