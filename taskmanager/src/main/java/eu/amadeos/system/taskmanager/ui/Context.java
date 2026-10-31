package eu.amadeos.system.taskmanager.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.taskmanager.control.ProcessControl;
import eu.amadeos.system.taskmanager.model.ProcessRow;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/**
 * What the windows of the Task Manager share: texts, the monitor, the process control and the way back to
 * the main window.
 *
 * @param texts           the texts in the user's language
 * @param monitor         the background sampling
 * @param control         ends, pauses and continues processes
 * @param ownUid          the user id of the Task Manager's user
 * @param showProcess     shows the main window with this process selected in the Processes tab
 * @param showProgram     shows the main window with this window selected in the Programs tab
 */
public record Context(Messages texts, Monitor monitor, ProcessControl control, int ownUid,
                      IntConsumer showProcess, LongConsumer showProgram) {

    /** Programs of the desktop itself: ending them ends the session or leaves the desktop without a part. */
    private static final Set<String> DESKTOP = Set.of("amadeos-session", "amadeos-wm", "amadeos-panel-cde",
            "amadeos-panel", "amadeos-dm", "Xorg", "X", "systemd", "dbus-daemon", "dbus-broker", "pipewire",
            "wireplumber", "NetworkManager", "polkitd");

    public Locale locale() {
        return texts.locale();
    }

    /** Whether ending the process hurts the desktop or the system. */
    public static boolean isImportant(ProcessRow row) {
        if (row.pid() <= 2) return true;
        String name = row.name();
        if (DESKTOP.contains(name)) return true;
        for (String d : DESKTOP) {
            if (row.info().command().contains("/opt/" + d + "/")) return true;
        }
        return false;
    }

    public boolean isOwn(ProcessRow row) {
        return row.info().uid() == ownUid;
    }

    /** Shows that an action failed; a cancelled password dialog shows nothing. */
    public void failed(Component parent, String verbKey, IOException e) {
        if (e instanceof ProcessControl.Cancelled) return;
        JOptionPane.showMessageDialog(parent, texts.format("error.action", texts.get(verbKey), e.getMessage()),
                texts.get("error.title"), JOptionPane.ERROR_MESSAGE);
    }
}
