package eu.amadeos.system.disks.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.disks.model.Drive;
import eu.amadeos.system.disks.model.Layout;
import eu.amadeos.system.disks.model.Sizes;
import eu.amadeos.system.disks.model.Volume;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.function.LongConsumer;

/**
 * Grow or shrink a partition with its file system. How far it can shrink depends on the space in use (read first,
 * mounting the file system for a moment if needed) and on whether udisks can shrink that type; partitions of the
 * running system only grow.
 */
final class ResizeDialog {
    /** Room to leave above the data in use when shrinking. */
    static final double HEADROOM = 1.10;

    private ResizeDialog() {
    }

    /** Reads what is needed off the event thread, then shows the dialog; {@code onResize} gets the new size. */
    static void show(Component owner, Context c, Drive drive, Volume v, long freeAfter, LongConsumer onResize) {
        owner.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<long[], Void>() {
            boolean[] can;

            @Override
            protected long[] doInBackground() throws Exception {
                can = c.service().canResize(v.content().type());
                long used = can[1] && !v.locked() ? c.service().usedBytes(v) : -1;
                return new long[]{used};
            }

            @Override
            protected void done() {
                owner.setCursor(Cursor.getDefaultCursor());
                try {
                    long used = get()[0];
                    dialog(owner, c, drive, v, freeAfter, can, used, onResize);
                } catch (Exception e) {
                    Errors.show(owner, c, e.getCause() != null ? e.getCause() : e);
                }
            }
        }.execute();
    }

    static long minimum(Volume v, boolean canShrink, long used) {
        if (!canShrink || v.locked() || used < 0) return v.size();
        long min = Layout.alignDown((long) (used * HEADROOM) + 32 * Sizes.MIB) + Layout.ALIGN;
        return Math.min(v.size(), Math.max(min, 32 * Sizes.MIB));
    }

    private static void dialog(Component owner, Context c, Drive drive, Volume v, long freeAfter, boolean[] can,
            long used, LongConsumer onResize) {
        Messages t = c.texts();
        long min = minimum(v, can[1], used);
        long max = can[0] ? v.size() + Layout.alignDown(freeAfter) : v.size();
        if (min >= max) {
            JOptionPane.showMessageDialog(owner, Dialogs.wrap(t.format("resize.impossible", c.volumeName(v),
                    c.fsShort(v)), 380), t.get("resize.title"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Window w = owner instanceof Window win ? win : javax.swing.SwingUtilities.getWindowAncestor(owner);
        JDialog d = new JDialog(w, t.get("resize.title"), java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        p.add(Dialogs.left(Dialogs.bold(t.format("resize.heading", c.volumeName(v), c.size(v.size())))));
        p.add(Box.createVerticalStrut(4));
        String limits = t.format("resize.limits", c.size(min), c.size(max));
        if (used >= 0) limits += "  ·  " + t.format("resize.used", c.size(used));
        p.add(Dialogs.left(Dialogs.hint(limits, 360)));
        p.add(Box.createVerticalStrut(8));
        SizeField size = new SizeField(c, min, max, v.size());
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.add(new JLabel(t.get("create.size")));
        row.add(size);
        p.add(Dialogs.left(row));
        p.add(Box.createVerticalStrut(6));
        if (v.locked()) p.add(Dialogs.left(Dialogs.hint(t.get("resize.grow-only"), 360)));
        else if (v.content().mounted() && can[1]) p.add(Dialogs.left(Dialogs.hint(t.get("resize.unmount"), 360)));
        p.add(Dialogs.left(Dialogs.hint(t.get("resize.backup"), 360)));
        JButton cancel = new JButton(t.get("button.cancel"));
        JButton go = new JButton(t.get("button.resize"));
        cancel.addActionListener(e -> d.dispose());
        go.addActionListener(e -> {
            long bytes = size.value();
            d.dispose();
            if (Math.abs(bytes - v.size()) >= Layout.ALIGN) onResize.accept(bytes);
        });
        p.add(Dialogs.left(Dialogs.buttons(cancel, go)));
        d.setContentPane(p);
        d.getRootPane().setDefaultButton(go);
        Dialogs.escapeCloses(d);
        d.pack();
        d.setResizable(false);
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }
}
