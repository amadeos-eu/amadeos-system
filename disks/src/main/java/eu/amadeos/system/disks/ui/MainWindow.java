package eu.amadeos.system.disks.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.system.base.process.ProcessLauncher;
import eu.amadeos.system.disks.model.Drive;
import eu.amadeos.system.disks.model.FsChoice;
import eu.amadeos.system.disks.model.Safety;
import eu.amadeos.system.disks.model.Segment;
import eu.amadeos.system.disks.model.Sizes;
import eu.amadeos.system.disks.model.Volume;
import eu.amadeos.system.disks.service.DiskException;
import eu.amadeos.system.disks.service.Progress;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The window of Disks: the drives on the left; on the right the chosen drive with its partition map, the chosen
 * partition's details and the buttons for what can be done with it. One step runs at a time; the status bar shows
 * it and how far it is.
 */
public final class MainWindow extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(MainWindow.class.getName());
    /** The chapter of the User's Handbook, in the package amadeos-handbook and on the web. */
    static final String HELP = "/usr/share/doc/amadeos-handbook/html/59-task-manager.html";
    static final String HELP_ONLINE = "https://docs.amadeos.eu/amadeos/59-task-manager/#disks";
    static final Color GOOD = new Color(0x2E, 0x7D, 0x32);
    static final Color BAD = new Color(0xB0, 0x20, 0x20);

    private final Context context;
    private final Messages t;
    private final DefaultListModel<Drive> driveModel = new DefaultListModel<>();
    private final JList<Drive> driveList = new JList<>(driveModel);
    private final JLabel title = new JLabel(" ");
    private final JLabel subtitle = new JLabel(" ");
    private final JLabel healthLine = new JLabel(" ");
    private final PartitionMap map;
    private final JPanel details = new JPanel(new GridBagLayout());
    private final JLabel note = new JLabel(" ");
    private final JLabel status = new JLabel(" ");
    private final JProgressBar progress = new JProgressBar(0, 1000);
    private final List<Action> actions = new ArrayList<>();
    private final AtomicBoolean busy = new AtomicBoolean();
    private final Timer refreshLater;
    private Timer progressTimer;
    private List<Drive> drives = List.of();
    /** Per file system type: can udisks grow it, shrink it (asked once per type). */
    private final java.util.Map<String, boolean[]> resizable = new java.util.concurrent.ConcurrentHashMap<>();
    private String pendingDevice;
    private boolean pendingFormat;

    // what can be done; enabled by update()
    final Action openFiles;
    final Action mount;
    final Action unmount;
    final Action unlock;
    final Action lock;
    final Action check;
    final Action rename;
    final Action resize;
    final Action format;
    final Action delete;
    final Action create;
    final Action formatDrive;
    final Action writeImage;
    final Action saveImage;
    final Action health;
    final Action remove;
    final Action eject;

    public MainWindow(Context context) {
        super(context.texts().get("app.title"));
        this.context = context;
        this.t = context.texts();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (busy.get() && JOptionPane.showConfirmDialog(MainWindow.this, t.get("close.busy"),
                        t.get("app.title"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                        != JOptionPane.YES_OPTION) {
                    return;
                }
                dispose();
            }
        });
        map = new PartitionMap(context, i -> update());

        openFiles = action("action.open-files", this::openInFiles);
        mount = action("action.mount", () -> runOn("status.mounting", v -> context.service().mount(v)));
        unmount = action("action.unmount", () -> runOn("status.unmounting", v -> context.service().unmount(v)));
        unlock = action("action.unlock", this::unlockVolume);
        lock = action("action.lock", () -> runOn("status.locking", v -> context.service().lock(v)));
        check = action("action.check", this::checkVolume);
        rename = action("action.rename", this::renameVolume);
        resize = action("action.resize", this::resizeVolume);
        format = action("action.format", this::formatVolume);
        delete = action("action.delete", this::deleteVolume);
        create = action("action.create", this::createPartition);
        formatDrive = action("action.format-drive", this::formatWholeDrive);
        writeImage = action("action.write-image", () -> ImageDialog.write(this, context, drive(), drives,
                this::refresh, busy));
        saveImage = action("action.save-image", () -> ImageDialog.save(this, context, drive(), busy));
        health = action("action.health", () -> new HealthDialog(this, context, drive()).setVisible(true));
        remove = action("action.safely-remove", () -> runDrive("status.removing",
                d -> context.service().powerOff(d)));
        eject = action("action.eject", () -> runDrive("status.ejecting", d -> context.service().eject(d)));

        setJMenuBar(menus());
        JPanel body = new JPanel(new BorderLayout(6, 0));
        body.setBorder(BorderFactory.createEmptyBorder(6, 6, 0, 6));
        body.add(driveList(), BorderLayout.WEST);
        body.add(drivePanel(), BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
        add(statusBar(), BorderLayout.SOUTH);

        refreshLater = new Timer(400, e -> refresh());
        refreshLater.setRepeats(false);
        getRootPane().registerKeyboardAction(e -> refresh(), KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setSize(new Dimension(860, 560));
        setMinimumSize(new Dimension(640, 440));
        setLocationRelativeTo(null);
        update();
    }

    // ---- building -----------------------------------------------------------------------------------------------

    private Action action(String key, Runnable run) {
        Action a = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isEnabled()) run.run();
            }
        };
        Mnemonics.Text m = Mnemonics.parse(t.get(key));
        a.putValue(Action.NAME, m.text());
        a.putValue(Action.ACTION_COMMAND_KEY, key);
        if (m.index() >= 0) a.putValue(Action.MNEMONIC_KEY, m.keyCode());
        actions.add(a);
        return a;
    }

    private JMenuBar menus() {
        JMenuBar bar = new JMenuBar();
        JMenu drive = Mnemonics.apply(new JMenu(), t.get("menu.drive"));
        for (Action a : List.of(formatDrive, writeImage, saveImage)) drive.add(a);
        drive.addSeparator();
        drive.add(health);
        drive.addSeparator();
        drive.add(remove);
        drive.add(eject);
        drive.addSeparator();
        JMenuItem close = Mnemonics.apply(new JMenuItem(), t.get("menu.drive.close"));
        close.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.CTRL_DOWN_MASK));
        close.addActionListener(e -> dispatchEvent(new java.awt.event.WindowEvent(this,
                java.awt.event.WindowEvent.WINDOW_CLOSING)));
        drive.add(close);
        bar.add(drive);

        JMenu part = Mnemonics.apply(new JMenu(), t.get("menu.partition"));
        part.add(create);
        part.add(format);
        part.add(delete);
        part.addSeparator();
        part.add(resize);
        part.add(rename);
        part.add(check);
        part.addSeparator();
        part.add(mount);
        part.add(unmount);
        part.add(unlock);
        part.add(lock);
        part.add(openFiles);
        bar.add(part);

        JMenu view = Mnemonics.apply(new JMenu(), t.get("menu.view"));
        JMenuItem refresh = Mnemonics.apply(new JMenuItem(), t.get("menu.view.refresh"));
        refresh.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        refresh.addActionListener(e -> refresh());
        view.add(refresh);
        bar.add(view);

        JMenu help = Mnemonics.apply(new JMenu(), t.get("menu.help"));
        JMenuItem contents = Mnemonics.apply(new JMenuItem(), t.get("menu.help.contents"));
        contents.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
        contents.addActionListener(e -> launch(List.of("xdg-open",
                new File(HELP).isFile() ? HELP + "#disks" : HELP_ONLINE)));
        JMenuItem about = Mnemonics.apply(new JMenuItem(), t.get("menu.help.about"));
        about.addActionListener(e -> JOptionPane.showMessageDialog(this, t.get("about.text"), t.get("app.title"),
                JOptionPane.INFORMATION_MESSAGE));
        help.add(contents);
        help.addSeparator();
        help.add(about);
        bar.add(help);
        return bar;
    }

    private JComponent driveList() {
        driveList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        driveList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                    boolean focus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
                Drive d = (Drive) value;
                String second = d.hasMedium()
                        ? (d.title().isEmpty() ? "" : d.title() + " · ") + context.size(d.size())
                        : t.get("drive.no-medium");
                l.setText("<html><b>" + Dialogs.escape(context.kind(d)) + "</b><br><small>"
                        + Dialogs.escape(second) + "</small></html>");
                l.setIcon(DriveIcons.of(d.kind(), 32));
                l.setIconTextGap(8);
                l.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 8));
                return l;
            }
        });
        driveList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showDrive(0);
        });
        JScrollPane sp = new JScrollPane(driveList);
        sp.setPreferredSize(new Dimension(210, 300));
        return sp;
    }

    private JComponent drivePanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 3f));
        p.add(Dialogs.left(title));
        p.add(Dialogs.left(subtitle));
        p.add(Dialogs.left(healthLine));
        p.add(Box.createVerticalStrut(8));
        p.add(Dialogs.left(new JLabel(t.get("label.partitions"))));
        p.add(Box.createVerticalStrut(3));
        map.setAlignmentX(0);
        map.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        p.add(map);
        p.add(Box.createVerticalStrut(8));
        details.setAlignmentX(0);
        p.add(details);
        p.add(Box.createVerticalStrut(6));
        note.setForeground(AmadeUI.theme().textInactive());
        p.add(Dialogs.left(note));
        p.add(Box.createVerticalStrut(8));
        p.add(row(openFiles, mount, unmount, unlock, lock, check, rename));
        p.add(Box.createVerticalStrut(4));
        p.add(row(create, resize, format, delete));
        p.add(Box.createVerticalStrut(10));
        p.add(row(formatDrive, writeImage, saveImage, health, remove, eject));
        p.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        // The content keeps its height at the top; the scroll pane only scrolls up and down.
        WidthTrackingPanel holder = new WidthTrackingPanel();
        holder.add(p, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(holder, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(BorderFactory.createEmptyBorder());
        return sp;
    }

    /**
     * A row of buttons. Buttons of steps that do not fit the chosen drive or partition are hidden, so each row
     * shows only what can be done; while a step runs, the others are only disabled.
     */
    private static JComponent row(Action... list) {
        JPanel p = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 4));
        p.setAlignmentX(0);
        for (Action a : list) {
            JButton b = new JButton(a);
            // The menus carry the mnemonics; on the buttons they would clash with the menu bar's.
            b.setMnemonic(0);
            b.setVisible(Boolean.TRUE.equals(a.getValue(APPLICABLE)));
            a.addPropertyChangeListener(e -> {
                if (APPLICABLE.equals(e.getPropertyName())) {
                    b.setVisible(Boolean.TRUE.equals(e.getNewValue()));
                    p.revalidate();
                    p.repaint();
                }
            });
            p.add(b);
        }
        return p;
    }

    /** Whether a step fits the chosen drive or partition (its button is shown). */
    static final String APPLICABLE = "amadeos.applicable";

    private static void set(Action a, boolean applicable, boolean idle) {
        a.putValue(APPLICABLE, applicable);
        a.setEnabled(applicable && idle);
    }

    private JComponent statusBar() {
        status.setBorder(BorderFactory.createCompoundBorder(AmadeUI.sunken(),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        progress.setVisible(false);
        progress.setPreferredSize(new Dimension(180, 18));
        JPanel bottom = new JPanel(new BorderLayout(6, 0));
        bottom.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
        bottom.add(status, BorderLayout.CENTER);
        bottom.add(progress, BorderLayout.EAST);
        return bottom;
    }

    // ---- data ---------------------------------------------------------------------------------------------------

    /** Reads the drives anew (off the event thread) and shows them, keeping the selection. */
    public void refresh() {
        new SwingWorker<List<Drive>, Void>() {
            @Override
            protected List<Drive> doInBackground() throws Exception {
                List<Drive> list = context.service().drives();
                for (Drive d : list) {
                    for (Volume v : d.volumes()) {
                        String type = v.content().type();
                        if (!type.isEmpty()) resizable.computeIfAbsent(type, context.service()::canResize);
                    }
                }
                return list;
            }

            @Override
            protected void done() {
                try {
                    show(get());
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    LOGGER.log(Level.WARNING, "Cannot read the drives", cause);
                    status.setText(t.get("status.no-udisks"));
                }
            }
        }.execute();
    }

    /** Refreshes a moment later, so a burst of changes from udisks makes one refresh. */
    public void refreshSoon() {
        SwingUtilities.invokeLater(refreshLater::restart);
    }

    /** Selects the drive of a device such as /dev/sdb (and opens Format Drive for it) once it is listed. */
    public void select(String device, boolean format) {
        pendingDevice = device;
        pendingFormat = format;
        if (!drives.isEmpty()) show(drives);
    }

    void show(List<Drive> list) {
        Drive was = driveList.getSelectedValue();
        Segment seg = segment();
        drives = List.copyOf(list);
        driveModel.clear();
        for (Drive d : drives) driveModel.addElement(d);
        int index = 0;
        boolean format = false;
        for (int i = 0; i < drives.size(); i++) {
            if (was != null && drives.get(i).id().equals(was.id())) index = i;
        }
        if (pendingDevice != null) {
            for (int i = 0; i < drives.size(); i++) {
                if (drives.get(i).device().equals(pendingDevice)) {
                    index = i;
                    format = pendingFormat;
                }
            }
            pendingDevice = null;
        }
        if (!drives.isEmpty()) driveList.setSelectedIndex(index);
        Drive now = driveList.getSelectedValue();
        int segIndex = 0;
        if (now != null && seg != null && was != null && was.id().equals(now.id())) {
            for (int i = 0; i < now.segments().size(); i++) {
                Segment s = now.segments().get(i);
                if (s.offset() == seg.offset() || (!s.free() && seg.volume() != null
                        && s.volume().id().equals(seg.volume().id()))) {
                    segIndex = i;
                }
            }
        }
        showDrive(segIndex);
        if (!busy.get()) status.setText(summary());
        if (format && formatDrive.isEnabled()) SwingUtilities.invokeLater(this::formatWholeDrive);
    }

    private String summary() {
        Drive system = drives.stream().filter(Drive::system).findFirst().orElse(null);
        String count = t.format("status.drives", Integer.toString(drives.size()));
        if (system != null && system.smart()) {
            String h = system.healthOk() ? t.get("health.short.good") : t.get("health.short.bad");
            if (system.temperature() >= 0) h += ", " + system.temperature() + " °C";
            return count + "    " + t.format("status.system", h);
        }
        return count;
    }

    private void showDrive(int segmentIndex) {
        Drive d = drive();
        if (d == null) {
            title.setText(t.get("drive.none"));
            subtitle.setText(" ");
            healthLine.setText(" ");
            map.setSegments(List.of(), -1);
        } else {
            title.setText(d.title().isEmpty() ? context.kind(d) : d.title());
            List<String> parts = new ArrayList<>();
            if (d.size() > 0) parts.add(context.size(d.size()));
            parts.add(connection(d));
            if (d.hasMedium()) {
                parts.add(d.hasTable() ? t.get("table." + d.tableType()) : t.get("table.none"));
            }
            if (!d.backingFile().isEmpty()) parts.add(d.backingFile());
            subtitle.setText(String.join("  ·  ", parts));
            if (d.kind() == Drive.Kind.IMAGE) {
                healthLine.setText(" ");
            } else if (!d.smart()) {
                healthLine.setText("● " + t.get(d.kind() == Drive.Kind.STICK || d.kind() == Drive.Kind.CARD
                        ? "health.not-reported.stick" : "health.not-reported"));
                healthLine.setForeground(AmadeUI.theme().textInactive());
            } else if (d.healthOk()) {
                healthLine.setText("● " + t.get("health.line.good"));
                healthLine.setForeground(GOOD);
            } else {
                healthLine.setText("● " + t.get("health.line.bad"));
                healthLine.setForeground(BAD);
            }
            map.setSegments(d.segments(), segmentIndex);
        }
        update();
    }

    private String connection(Drive d) {
        String key = "connection." + (d.connection().isEmpty() ? "other" : d.connection());
        String place = t.has(key) ? t.get(key) : d.connection();
        return place;
    }

    Drive drive() {
        return driveList.getSelectedValue();
    }

    Segment segment() {
        Drive d = drive();
        int i = map.selected();
        if (d == null || i < 0 || i >= d.segments().size()) return null;
        return d.segments().get(i);
    }

    Volume volume() {
        Segment s = segment();
        return s == null ? null : s.volume();
    }

    /** Shows the chosen block's details and enables what can be done with it. */
    private void update() {
        Drive d = drive();
        Segment s = segment();
        Volume v = volume();
        details.removeAll();
        note.setText(" ");
        if (s != null && v == null) {
            detail(0, t.get("detail.free"), context.size(s.size()));
            if (!d.hasTable()) note.setText(Dialogs.wrap(t.get("note.no-table"), 420));
        } else if (v != null) {
            Volume c = v.content();
            int row = 0;
            detail(row++, t.get("detail.name"), v.name().isEmpty() ? "–" : v.name());
            detail(row++, t.get("detail.type"), context.fsName(v));
            String size = context.size(v.size());
            if (c.used() >= 0) {
                size = t.format("detail.size.used", size, context.size(c.used()),
                        Integer.toString(Sizes.percent(c.used(), c.size() > 0 ? c.size() : v.size())));
            }
            detail(row++, t.get("detail.size"), size);
            String where = c.mountPoints().isEmpty() ? (v.swapActive() ? t.get("detail.swap-active")
                    : t.get("detail.not-mounted")) : String.join(", ", c.mountPoints());
            if (c.mountPoints().contains("/")) where = t.get("detail.running-system");
            detail(row++, t.get("detail.mounted"), where);
            detail(row++, t.get("detail.device"), v.device() + (v.cleartext() != null
                    ? "  →  " + v.cleartext().device() : ""));
            if (!c.uuid().isEmpty()) detail(row++, t.get("detail.uuid"), c.uuid());
            if (v.locked()) note.setText(Dialogs.wrap(t.get("note.locked"), 420));
        }
        details.revalidate();
        details.repaint();
        enable(d, s, v);
    }

    private void detail(int row, String key, String value) {
        GridBagConstraints g = new GridBagConstraints();
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(1, 2, 1, 12);
        g.gridy = row;
        g.gridx = 0;
        JLabel k = new JLabel(key);
        k.setForeground(AmadeUI.theme().textInactive());
        details.add(k, g);
        g.gridx = 1;
        g.weightx = 1;
        JLabel val = new JLabel(value);
        details.add(val, g);
    }

    private void enable(Drive d, Segment s, Volume v) {
        boolean idle = !busy.get();
        Volume c = v == null ? null : v.content();
        boolean fs = c != null && c.filesystem();
        boolean medium = d != null && d.hasMedium();
        boolean writable = medium && d.kind() != Drive.Kind.DISC;
        set(openFiles, fs && c.mounted(), true);
        set(mount, fs && !c.mounted(), idle);
        set(unmount, fs && c.mounted() && !v.locked(), idle);
        set(unlock, v != null && v.encrypted() && v.cleartext() == null, idle);
        set(lock, v != null && v.encrypted() && v.cleartext() != null && !v.locked(), idle);
        set(check, fs && !v.locked() && writable, idle);
        set(rename, fs && writable && !v.locked() && FsChoice.of(c.type()) != null, idle);
        boolean[] can = c == null ? null : resizable.getOrDefault(c.type(), new boolean[]{false, false});
        set(resize, v != null && v.partition() && writable && fs && !v.encrypted()
                && (can[0] && canGrow(d, v) || can[1] && !v.locked()), idle);
        set(format, v != null && writable && Safety.canFormat(v), idle);
        set(delete, v != null && writable && Safety.canDelete(v), idle);
        set(create, s != null && s.free() && writable && d.hasTable(), idle);
        set(formatDrive, writable && !d.system(), idle);
        set(writeImage, writable && !d.system(), idle);
        set(saveImage, writable && !d.system(), idle);
        set(health, d != null && d.smart(), true);
        set(remove, d != null && d.canPowerOff() && !d.system(), idle);
        set(eject, d != null && d.ejectable() && !d.system(), idle);
    }

    /** Free space right after the volume, so it can grow. */
    static long freeAfter(Drive d, Volume v) {
        List<Segment> list = d.segments();
        for (int i = 0; i + 1 < list.size(); i++) {
            Segment s = list.get(i);
            if (!s.free() && s.volume().id().equals(v.id()) && list.get(i + 1).free()) return list.get(i + 1).size();
        }
        return 0;
    }

    private static boolean canGrow(Drive d, Volume v) {
        return freeAfter(d, v) > 0;
    }

    // ---- steps --------------------------------------------------------------------------------------------------

    /** A step on a drive, run off the event thread. */
    @FunctionalInterface
    interface Step {
        void run() throws Exception;
    }

    @FunctionalInterface
    interface VolumeStep {
        void run(Volume v) throws Exception;
    }

    @FunctionalInterface
    interface DriveStep {
        void run(Drive d) throws Exception;
    }

    private void runOn(String statusKey, VolumeStep step) {
        Volume v = volume();
        if (v == null) return;
        run(t.format(statusKey, context.volumeName(v)), v.id(), () -> step.run(v), null);
    }

    private void runDrive(String statusKey, DriveStep step) {
        Drive d = drive();
        if (d == null) return;
        run(t.format(statusKey, context.kind(d)), d.block(), () -> step.run(d), null);
    }

    // --8<-- [start:run]
    /**
     * Runs one step: the window shows it in the status bar (with udisks' progress where a job reports it), keeps the
     * other steps disabled, and reads the drives anew afterwards. Errors are explained; a closed password dialog
     * is not an error.
     */
    void run(String what, String blockId, Step step, Runnable after) {
        if (!busy.compareAndSet(false, true)) return;
        status.setText(what);
        progress.setIndeterminate(true);
        progress.setVisible(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        enable(drive(), segment(), volume());
        progressTimer = new Timer(700, e -> pollProgress(blockId));
        progressTimer.start();
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                step.run();
                return null;
            }

            @Override
            protected void done() {
                progressTimer.stop();
                busy.set(false);
                progress.setVisible(false);
                setCursor(Cursor.getDefaultCursor());
                status.setText(summary());
                try {
                    get();
                    if (after != null) after.run();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    LOGGER.log(cause instanceof DiskException ? Level.INFO : Level.WARNING, what, cause);
                    Errors.show(MainWindow.this, context, cause);
                }
                refresh();
            }
        }.execute();
    }
    // --8<-- [end:run]

    private void pollProgress(String blockId) {
        new SwingWorker<Progress, Void>() {
            @Override
            protected Progress doInBackground() {
                return context.service().progress(blockId);
            }

            @Override
            protected void done() {
                try {
                    Progress p = get();
                    if (!busy.get()) return;
                    if (p.fraction() >= 0) {
                        progress.setIndeterminate(false);
                        progress.setValue((int) Math.round(p.fraction() * 1000));
                    } else {
                        progress.setIndeterminate(true);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "No progress", e);
                }
            }
        }.execute();
    }

    private void openInFiles() {
        Volume v = volume();
        if (v != null && v.content().mounted()) launch(List.of("xdg-open", v.content().mountPoints().get(0)));
    }

    private void launch(List<String> command) {
        try {
            ProcessLauncher.start(command);
        } catch (IOException e) {
            Errors.show(this, context, e);
        }
    }

    private void unlockVolume() {
        Volume v = volume();
        if (v == null) return;
        JPasswordField field = new JPasswordField(20);
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.add(new JLabel(t.format("unlock.prompt", context.volumeName(v), context.size(v.size()))),
                BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        SwingUtilities.invokeLater(field::requestFocusInWindow);
        if (JOptionPane.showConfirmDialog(this, p, t.get("unlock.title"), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        char[] pass = field.getPassword();
        run(t.format("status.unlocking", context.volumeName(v)), v.id(), () -> {
            try {
                context.service().unlock(v, pass);
            } finally {
                java.util.Arrays.fill(pass, '\0');
            }
        }, null);
    }

    private void checkVolume() {
        Volume v = volume();
        if (v == null) return;
        if (v.content().mounted() && JOptionPane.showConfirmDialog(this,
                Dialogs.wrap(t.get("check.unmount"), 380), t.get("check.title"), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        boolean[] ok = {false};
        run(t.format("status.checking", context.volumeName(v)), v.content().id(),
                () -> ok[0] = context.service().check(v), () -> {
                    if (ok[0]) {
                        JOptionPane.showMessageDialog(this, t.format("check.ok", context.volumeName(v)),
                                t.get("check.title"), JOptionPane.INFORMATION_MESSAGE);
                    } else if (JOptionPane.showConfirmDialog(this, Dialogs.wrap(t.format("check.errors",
                            context.volumeName(v)), 380), t.get("check.title"), JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
                        repair(v);
                    }
                });
    }

    private void repair(Volume v) {
        boolean[] ok = {false};
        run(t.format("status.repairing", context.volumeName(v)), v.content().id(),
                () -> ok[0] = context.service().repair(v), () -> JOptionPane.showMessageDialog(this,
                        Dialogs.wrap(t.format(ok[0] ? "repair.ok" : "repair.failed", context.volumeName(v)), 380),
                        t.get("check.title"), ok[0] ? JOptionPane.INFORMATION_MESSAGE
                                : JOptionPane.ERROR_MESSAGE));
    }

    private void renameVolume() {
        Volume v = volume();
        if (v == null) return;
        FsChoice kind = FsChoice.of(v.content().type());
        Object answer = JOptionPane.showInputDialog(this, t.format("rename.prompt",
                Integer.toString(kind.maxLabel())), t.get("rename.title"), JOptionPane.PLAIN_MESSAGE, null, null,
                v.content().label());
        if (answer == null) return;
        String label = kind.label(answer.toString());
        run(t.format("status.renaming", context.volumeName(v)), v.content().id(),
                () -> context.service().setLabel(v, label), null);
    }

    private void resizeVolume() {
        Drive d = drive();
        Volume v = volume();
        if (d == null || v == null) return;
        ResizeDialog.show(this, context, d, v, freeAfter(d, v), size -> run(t.format("status.resizing",
                context.volumeName(v)), v.id(), () -> context.service().resize(v, size), null));
    }

    private void formatVolume() {
        Drive d = drive();
        Volume v = volume();
        if (d == null || v == null) return;
        FormatDialog.Result r = FormatDialog.ask(this, context, t.format("format.heading.volume",
                context.volumeName(v), context.size(v.size())), v.size(), d.removable(), false,
                v.content().label());
        if (r == null) return;
        List<String> lost = new ArrayList<>();
        lost.add(lostLine(v));
        if (!Dialogs.confirm(this, context, t.get("confirm.title.volume"), t.format("confirm.heading.volume",
                context.volumeName(v), context.size(v.size()), d.title().isEmpty() ? context.kind(d) : d.title()),
                lost, t.format("confirm.after", r.choiceName(context), r.label().isEmpty() ? "–" : "“" + r.label() + "”"),
                t.get("confirm.action.format"))) {
            return;
        }
        run(t.format("status.formatting", context.volumeName(v)), v.id(), () -> {
            try {
                context.service().formatVolume(v, r.choice(), r.label(), r.overwrite(), r.passphrase());
            } finally {
                r.clear();
            }
        }, null);
    }

    private void formatWholeDrive() {
        Drive d = drive();
        if (d == null) return;
        String name = d.title().isEmpty() ? context.kind(d) : d.title();
        FormatDialog.Result r = FormatDialog.ask(this, context, t.format("format.heading.drive",
                context.kind(d), name, context.size(d.size())), d.size(), d.removable()
                || d.kind() == Drive.Kind.STICK || d.kind() == Drive.Kind.CARD || d.kind() == Drive.Kind.EXTERNAL,
                true, d.volumes().isEmpty() ? "" : d.volumes().get(0).content().label());
        if (r == null) return;
        List<String> lost = new ArrayList<>();
        for (Volume v : d.volumes()) lost.add(lostLine(v));
        if (!Dialogs.confirm(this, context, t.format("confirm.title.drive", context.kind(d)),
                t.format("confirm.heading.drive", name, context.size(d.size())), lost,
                t.format("confirm.after", r.choiceName(context), r.label().isEmpty() ? "–" : "“" + r.label() + "”"),
                t.get("confirm.action.erase"))) {
            return;
        }
        run(t.format("status.formatting", context.kind(d)), d.block(), () -> {
            try {
                context.service().formatDrive(d, r.choice(), r.label(), r.overwrite(), r.passphrase());
            } finally {
                r.clear();
            }
        }, null);
    }

    private String lostLine(Volume v) {
        Volume c = v.content();
        String line = context.volumeName(v) + ", " + context.fsShort(v);
        if (c.used() >= 0) line += ", " + t.format("confirm.used", context.size(c.used()));
        else line += ", " + context.size(v.size());
        return line;
    }

    private void deleteVolume() {
        Drive d = drive();
        Volume v = volume();
        if (d == null || v == null) return;
        if (!Dialogs.confirm(this, context, t.get("confirm.title.delete"), t.format("confirm.heading.delete",
                context.volumeName(v), context.size(v.size())), List.of(lostLine(v)), t.get("confirm.after.delete"),
                t.get("confirm.action.delete"))) {
            return;
        }
        run(t.format("status.deleting", context.volumeName(v)), v.id(), () -> context.service().deletePartition(v),
                null);
    }

    private void createPartition() {
        Drive d = drive();
        Segment s = segment();
        if (d == null || s == null || !s.free()) return;
        NewPartitionDialog.Result r = NewPartitionDialog.ask(this, context, d, s);
        if (r == null) return;
        run(t.format("status.creating", r.label().isEmpty() ? context.size(r.size()) : r.label()), d.block(), () -> {
            try {
                context.service().createPartition(d, s.offset(), r.size(), r.choice(), r.label(), r.passphrase(),
                        r.mountAtStart());
            } finally {
                r.clear();
            }
        }, null);
    }

    // For the tests and the pictures of the handbook.
    PartitionMap partitionMap() {
        return map;
    }

    JList<Drive> drives() {
        return driveList;
    }

    /** The action of a text key such as {@code action.format-drive}. */
    Action action(String key) {
        for (Action a : actions) {
            if (key.equals(a.getValue(Action.ACTION_COMMAND_KEY))) return a;
        }
        throw new IllegalArgumentException(key);
    }

    boolean isBusy() {
        return busy.get();
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
