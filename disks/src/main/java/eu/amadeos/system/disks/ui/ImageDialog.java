package eu.amadeos.system.disks.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.disks.image.ImageCopy;
import eu.amadeos.system.disks.image.ImageJobs;
import eu.amadeos.system.disks.model.Drive;
import eu.amadeos.system.disks.service.Progress;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Writes a disk image (.iso, .img, .img.xz) to a stick or card, checking the published SHA-256 first and comparing
 * the written data at the end; or saves a drive as an image file. The window stays open while it runs and can stop
 * it.
 */
final class ImageDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    private final Context c;
    private final Messages t;
    private final AtomicBoolean busy;
    private final AtomicBoolean stop = new AtomicBoolean();
    private final JProgressBar bar = new JProgressBar(0, 1000);
    private final JLabel below = new JLabel(" ");
    private final JButton stopButton;
    private final JButton closeButton;
    private long started;

    private ImageDialog(Window owner, Context c, String title, AtomicBoolean busy) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.c = c;
        this.t = c.texts();
        this.busy = busy;
        bar.setStringPainted(true);
        bar.setString(" ");
        below.setForeground(eu.amadeos.commons.swing.AmadeUI.theme().textInactive());
        stopButton = new JButton(t.get("button.stop"));
        closeButton = new JButton(t.get("button.close"));
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stop.set(true));
        closeButton.addActionListener(e -> closeAsked());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeAsked();
            }
        });
        Dialogs.escapeCloses(this);
    }

    private void closeAsked() {
        if (stopButton.isEnabled()) {
            if (JOptionPane.showConfirmDialog(this, t.get("image.stop-question"), getTitle(),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
                stop.set(true);
            }
            return;
        }
        dispose();
    }

    // ---- write --------------------------------------------------------------------------------------------------

    /** Write Image: choose the file and the drive (the chosen one first), then write. */
    static void write(Component owner, Context c, Drive chosen, List<Drive> drives, Runnable after,
            AtomicBoolean busy) {
        Messages t = c.texts();
        Window w = owner instanceof Window win ? win : SwingUtilities.getWindowAncestor(owner);
        ImageDialog d = new ImageDialog(w, c, t.get("image.write.title"), busy);
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JPanel fileRow = new JPanel(new BorderLayout(8, 0));
        fileRow.add(new JLabel(t.get("image.file")), BorderLayout.WEST);
        JTextField file = new JTextField(26);
        file.setEditable(false);
        fileRow.add(file, BorderLayout.CENTER);
        JButton choose = new JButton(t.get("button.choose"));
        fileRow.add(choose, BorderLayout.EAST);
        p.add(Dialogs.left(fileRow));
        p.add(Box.createVerticalStrut(4));
        JLabel info = Dialogs.hint(t.get("image.none"), 380);
        p.add(Dialogs.left(info));
        p.add(Box.createVerticalStrut(8));
        JPanel driveRow = new JPanel(new BorderLayout(8, 0));
        driveRow.add(new JLabel(t.get("image.drive")), BorderLayout.WEST);
        JComboBox<Drive> target = new JComboBox<>();
        for (Drive dr : drives) {
            if (dr.hasMedium() && !dr.system() && dr.kind() != Drive.Kind.DISC) target.addItem(dr);
        }
        if (chosen != null) target.setSelectedItem(chosen);
        target.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean s,
                    boolean f) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, s, f);
                if (value instanceof Drive dr) l.setText(c.driveLine(dr));
                return l;
            }
        });
        driveRow.add(target, BorderLayout.CENTER);
        p.add(Dialogs.left(driveRow));
        p.add(Box.createVerticalStrut(10));
        p.add(Dialogs.left(d.bar));
        p.add(Box.createVerticalStrut(4));
        p.add(Dialogs.left(d.below));
        d.below.setText(t.get("image.verify-note"));
        JButton go = new JButton(t.get("button.write"));
        go.setEnabled(false);
        Path[] image = {null};
        long[] size = {-1};
        choose.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(downloads());
            fc.setFileFilter(new FileNameExtensionFilter(t.get("image.filter"), "iso", "img", "xz", "raw"));
            if (fc.showOpenDialog(d) != JFileChooser.APPROVE_OPTION) return;
            image[0] = fc.getSelectedFile().toPath();
            file.setText(image[0].toString());
            info.setText(Dialogs.wrap(t.get("image.checking"), 380));
            go.setEnabled(false);
            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() throws Exception {
                    size[0] = ImageJobs.imageSize(image[0]);
                    String line = c.size(size[0]);
                    Optional<String> published = ImageCopy.publishedSha256(image[0]);
                    if (published.isEmpty()) return line + "  ·  " + t.get("image.sum.none");
                    String actual;
                    try (var in = Files.newInputStream(image[0])) {
                        actual = ImageCopy.sha256(new java.io.BufferedInputStream(in, 1 << 22), -1,
                                (x, y) -> { }, () -> false);
                    }
                    return line + "  ·  " + t.get(actual.equals(published.get()) ? "image.sum.ok" : "image.sum.bad");
                }

                @Override
                protected void done() {
                    try {
                        String text = get();
                        info.setText(Dialogs.wrap(text, 380));
                        info.setForeground(text.endsWith(t.get("image.sum.bad")) ? MainWindow.BAD
                                : eu.amadeos.commons.swing.AmadeUI.theme().textInactive());
                        go.setEnabled(target.getItemCount() > 0);
                    } catch (Exception ex) {
                        info.setText(Dialogs.wrap(t.format("error.failed", String.valueOf(ex.getMessage())), 380));
                    }
                }
            }.execute();
        });
        go.addActionListener(e -> {
            Drive dr = (Drive) target.getSelectedItem();
            if (dr == null || image[0] == null) return;
            if (size[0] > dr.size()) {
                JOptionPane.showMessageDialog(d, t.format("image.too-big", c.size(size[0]), c.size(dr.size())),
                        d.getTitle(), JOptionPane.WARNING_MESSAGE);
                return;
            }
            List<String> lost = dr.volumes().stream().map(v -> c.volumeName(v) + ", " + c.fsShort(v) + ", "
                    + c.size(v.size())).toList();
            String name = dr.title().isEmpty() ? c.kind(dr) : dr.title();
            if (!Dialogs.confirm(d, c, t.format("confirm.title.drive", c.kind(dr)),
                    t.format("confirm.heading.drive", name, c.size(dr.size())), lost,
                    t.format("image.confirm.after", image[0].getFileName().toString()),
                    t.get("confirm.action.write"))) {
                return;
            }
            choose.setEnabled(false);
            target.setEnabled(false);
            go.setEnabled(false);
            d.start(() -> {
                boolean ok = ImageJobs.write(c.service(), dr, image[0], d::progress, d.stop::get);
                return t.get(ok ? "image.done.verified" : "image.done.mismatch");
            }, after);
        });
        p.add(Dialogs.left(Dialogs.buttons(d.stopButton, d.closeButton, go)));
        d.setContentPane(p);
        d.pack();
        d.setResizable(false);
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }

    // ---- save ---------------------------------------------------------------------------------------------------

    /** Save Image: choose where, then read the whole drive into the file (packed with xz for .img.xz). */
    static void save(Component owner, Context c, Drive drive, AtomicBoolean busy) {
        Messages t = c.texts();
        JFileChooser fc = new JFileChooser(downloads());
        String base = (drive.title().isEmpty() ? c.kind(drive) : drive.title()).replaceAll("[^\\p{L}\\p{N}._-]+", "-");
        fc.setSelectedFile(new File(downloads(), base + "-" + LocalDate.now() + ".img.xz"));
        fc.setFileFilter(new FileNameExtensionFilter(t.get("image.filter"), "img", "xz"));
        if (fc.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) return;
        Path target = fc.getSelectedFile().toPath();
        if (Files.exists(target) && JOptionPane.showConfirmDialog(owner, t.format("image.replace",
                target.getFileName().toString()), t.get("image.save.title"), JOptionPane.YES_NO_OPTION)
                != JOptionPane.YES_OPTION) {
            return;
        }
        Window w = owner instanceof Window win ? win : SwingUtilities.getWindowAncestor(owner);
        ImageDialog d = new ImageDialog(w, c, t.get("image.save.title"), busy);
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        p.add(Dialogs.left(Dialogs.bold(t.format("image.save.heading", c.driveLine(drive)))));
        p.add(Dialogs.left(Dialogs.hint(target.toString(), 380)));
        p.add(Box.createVerticalStrut(10));
        p.add(Dialogs.left(d.bar));
        p.add(Box.createVerticalStrut(4));
        p.add(Dialogs.left(d.below));
        p.add(Dialogs.left(Dialogs.buttons(d.stopButton, d.closeButton)));
        d.setContentPane(p);
        d.pack();
        d.setResizable(false);
        d.setLocationRelativeTo(owner);
        d.start(() -> {
            ImageJobs.save(c.service(), drive, target, d::progress, d.stop::get);
            return t.format("image.done.saved", target.getFileName().toString());
        }, null);
        d.setVisible(true);
    }

    // ---- running ------------------------------------------------------------------------------------------------

    @FunctionalInterface
    interface Job {
        String run() throws Exception;
    }

    private void start(Job job, Runnable after) {
        busy.set(true);
        stop.set(false);
        stopButton.setEnabled(true);
        closeButton.setEnabled(true);
        bar.setIndeterminate(true);
        bar.setString(t.get("image.waiting"));
        started = System.nanoTime();
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return job.run();
            }

            @Override
            protected void done() {
                busy.set(false);
                stopButton.setEnabled(false);
                bar.setIndeterminate(false);
                try {
                    String text = get();
                    bar.setValue(1000);
                    bar.setString(text);
                    below.setText(" ");
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    bar.setValue(0);
                    if (cause instanceof ImageCopy.Stopped) {
                        bar.setString(t.get("image.stopped"));
                        below.setText(Dialogs.wrap(t.get("image.stopped.note"), 380));
                    } else {
                        bar.setString(t.get("image.failed"));
                        Errors.show(ImageDialog.this, c, cause instanceof IOException io
                                ? new eu.amadeos.system.disks.service.DiskException(
                                        eu.amadeos.system.disks.service.DiskException.Reason.FAILED,
                                        String.valueOf(io.getMessage()), io) : cause);
                    }
                }
                if (after != null) after.run();
            }
        }.execute();
    }

    /** Called from the copying thread. */
    void progress(ImageJobs.Phase phase, long done, long total) {
        double seconds = (System.nanoTime() - started) / 1e9;
        long rate = seconds > 0.5 ? (long) (done / seconds) : -1;
        Progress p = new Progress(total > 0 ? (double) done / total : -1, done, total, rate);
        SwingUtilities.invokeLater(() -> {
            bar.setIndeterminate(p.fraction() < 0);
            if (p.fraction() >= 0) bar.setValue((int) Math.round(p.fraction() * 1000));
            String key = "image.phase." + phase.name().toLowerCase(java.util.Locale.ROOT);
            String text = t.format(key, c.size(done), total > 0 ? c.size(total) : "?");
            if (rate > 0) text += "  ·  " + c.size(rate) + "/s";
            long left = p.secondsLeft();
            if (left >= 0) text += "  ·  " + t.format("image.left", duration(left));
            bar.setString(text);
        });
        if (phase != ImageJobs.Phase.READING && done == total) started = System.nanoTime();
    }

    static String duration(long seconds) {
        if (seconds >= 3600) return (seconds / 3600) + " h " + (seconds % 3600 / 60) + " min";
        if (seconds >= 60) return (seconds / 60) + " min " + (seconds % 60) + " s";
        return seconds + " s";
    }

    /** The user's downloads folder (XDG_DOWNLOAD_DIR), where images usually are; the home folder without it. */
    private static File downloads() {
        File d = eu.amadeos.commons.config.XdgDirectories.system().downloads().toFile();
        return d.isDirectory() ? d : new File(System.getProperty("user.home"));
    }
}
