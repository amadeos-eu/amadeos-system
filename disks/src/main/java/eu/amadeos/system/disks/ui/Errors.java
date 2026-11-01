package eu.amadeos.system.disks.ui;

import eu.amadeos.system.disks.service.DiskException;

import javax.swing.JOptionPane;
import java.awt.Component;

/** Shows what went wrong in words; a closed password dialog needs no message. */
final class Errors {
    private Errors() {
    }

    static void show(Component owner, Context c, Throwable t) {
        String text;
        if (t instanceof DiskException e) {
            text = switch (e.reason()) {
                case CANCELLED -> null;
                case NOT_ALLOWED -> c.texts().get("error.not-allowed");
                case BUSY -> c.texts().get("error.busy");
                case UNAVAILABLE -> c.texts().format("error.unavailable", e.getMessage());
                case FAILED -> c.texts().format("error.failed", e.getMessage());
            };
        } else {
            text = c.texts().format("error.failed", String.valueOf(t.getMessage()));
        }
        if (text == null) return;
        JOptionPane.showMessageDialog(owner, Dialogs.wrap(text, 420), c.texts().get("error.title"),
                JOptionPane.ERROR_MESSAGE);
    }
}
