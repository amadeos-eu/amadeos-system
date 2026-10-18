package eu.amadeos.print.ui;

import com.google.inject.Inject;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.print.cups.CupsClient;
import eu.amadeos.print.cups.Job;
import eu.amadeos.print.cups.Printer;
import eu.amadeos.print.ipp.IppException;
import eu.amadeos.print.service.TestPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * The print manager: the printers on top, the jobs of the chosen printer below, refreshed every few seconds.
 * CUPS is asked on the background executor; everything else happens on the event thread.
 */
public final class MainWindow extends JFrame {
    private static final Logger LOG = LoggerFactory.getLogger(MainWindow.class);
    private static final int REFRESH_MILLIS = 3000;
    /** Where printers are added and configured. */
    static final String CUPS_ADMIN = "http://localhost:631/admin";

    private final CupsClient cups;
    private final Messages msg;
    private final ExecutorService executor;
    private final EdtExecutor edt;
    private final DesktopLauncher launcher;
    private final String user;

    private final DefaultListModel<Printer> printers = new DefaultListModel<>();
    private final JList<Printer> printerList = new JList<>(printers);
    private final JobTableModel jobModel;
    private final JTable jobTable;
    private final CardLayout cards = new CardLayout();
    private final JPanel top = new JPanel(cards);
    private final JTextArea message = new JTextArea();
    private final JLabel status = new JLabel(" ");
    private final JCheckBoxMenuItem history = new JCheckBoxMenuItem();
    private final Timer timer = new Timer(REFRESH_MILLIS, e -> refresh());

    private final Action refresh;
    private final Action testPage;
    private final Action pause;
    private final Action resume;
    private final Action makeDefault;
    private final Action cancel;
    private final Action hold;
    private final Action release;

    private List<Job> allJobs = List.of();
    private boolean refreshing;

    @Inject
    public MainWindow(CupsClient cups, Messages msg, ExecutorService executor, EdtExecutor edt,
                      DesktopLauncher launcher) {
        super(msg.get("app.title"));
        this.cups = cups;
        this.msg = msg;
        this.executor = executor;
        this.edt = edt;
        this.launcher = launcher;
        this.user = System.getProperty("user.name", "");
        this.jobModel = new JobTableModel(msg, ZoneId.systemDefault());
        this.jobTable = new JTable(jobModel);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        refresh = action("action.refresh", KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), this::refresh);
        testPage = action("action.test-page", null, this::printTestPage);
        pause = action("action.pause", null, () -> onPrinter(cups::pause));
        resume = action("action.resume", null, () -> onPrinter(cups::resume));
        makeDefault = action("action.default", null, () -> onPrinter(cups::makeDefault));
        cancel = action("action.cancel", KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), this::cancelJob);
        hold = action("action.hold", null, () -> onJob(cups::hold));
        release = action("action.release", null, () -> onJob(cups::release));
        Action administration = action("action.admin", null, this::openAdministration);
        Action exit = action("action.exit", KeyStroke.getKeyStroke(KeyEvent.VK_Q, KeyEvent.CTRL_DOWN_MASK), this::dispose);

        JMenuBar bar = new JMenuBar();
        JMenu printerMenu = Mnemonics.apply(new JMenu(), msg.get("menu.printer"));
        for (Action a : new Action[]{testPage, pause, resume, makeDefault}) printerMenu.add(a);
        printerMenu.addSeparator();
        printerMenu.add(administration);
        printerMenu.addSeparator();
        printerMenu.add(exit);
        JMenu jobMenu = Mnemonics.apply(new JMenu(), msg.get("menu.job"));
        for (Action a : new Action[]{cancel, hold, release}) jobMenu.add(a);
        JMenu viewMenu = Mnemonics.apply(new JMenu(), msg.get("menu.view"));
        Mnemonics.apply(history, msg.get("action.history"));
        history.addActionListener(e -> refresh());
        viewMenu.add(history);
        viewMenu.addSeparator();
        viewMenu.add(refresh);
        bar.add(printerMenu);
        bar.add(jobMenu);
        bar.add(viewMenu);
        setJMenuBar(bar);

        printerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        printerList.setCellRenderer(new PrinterRenderer(msg));
        printerList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showJobs();
        });
        printerList.setComponentPopupMenu(popup(testPage, pause, resume, makeDefault));
        message.setEditable(false);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setOpaque(false);
        message.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        top.add(new JScrollPane(printerList), "list");
        top.add(message, "message");

        jobTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jobTable.setAutoCreateRowSorter(true);
        jobTable.setFillsViewportHeight(true);
        jobTable.getSelectionModel().addListSelectionListener(e -> updateActions());
        jobTable.setComponentPopupMenu(popup(cancel, hold, release));
        // A right click selects the row it is on, so that the menu acts on that job.
        jobTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = jobTable.rowAtPoint(e.getPoint());
                if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {
                    if (row >= 0) jobTable.setRowSelectionInterval(row, row);
                }
            }
        });
        int[] widths = {50, 260, 90, 70, 130, 90};
        for (int i = 0; i < widths.length; i++) jobTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, new JScrollPane(jobTable));
        split.setResizeWeight(0.4);
        split.setDividerLocation(190);
        add(split, BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(status, BorderLayout.SOUTH);

        updateActions();
        setSize(new Dimension(760, 540));
        setLocationRelativeTo(null);
    }

    /** Shows the window's content and starts following CUPS. */
    public void start() {
        refresh();
        timer.start();
    }

    @Override
    public void dispose() {
        timer.stop();
        super.dispose();
    }

    private record Snapshot(List<Printer> printers, List<Job> jobs) {
    }

    private void refresh() {
        if (refreshing) return;
        refreshing = true;
        boolean withHistory = history.isSelected();
        background(() -> new Snapshot(cups.printers(), cups.jobs(withHistory)), snapshot -> {
            refreshing = false;
            apply(snapshot);
        }, error -> {
            refreshing = false;
            showError(error);
        });
    }

    private void apply(Snapshot snapshot) {
        String selected = selectedPrinter().map(Printer::name).orElse(null);
        List<Printer> current = new ArrayList<>();
        for (int i = 0; i < printers.size(); i++) current.add(printers.get(i));
        if (!current.equals(snapshot.printers())) {
            printers.clear();
            printers.addAll(snapshot.printers());
        }
        if (snapshot.printers().isEmpty()) {
            showMessage(msg.get("status.no-printers"));
        } else {
            cards.show(top, "list");
            int index = indexOf(selected);
            if (index < 0) index = indexOf(snapshot.printers().stream().filter(Printer::isDefault).map(Printer::name)
                    .findFirst().orElse(null));
            if (index < 0) index = 0;
            if (printerList.getSelectedIndex() != index) printerList.setSelectedIndex(index);
        }
        allJobs = snapshot.jobs();
        showJobs();
        if (status.getText().equals(msg.get("status.no-cups"))) status.setText(" ");
    }

    private int indexOf(String name) {
        for (int i = 0; name != null && i < printers.size(); i++) {
            if (printers.get(i).name().equals(name)) return i;
        }
        return -1;
    }

    private void showJobs() {
        String printer = selectedPrinter().map(Printer::name).orElse("");
        int selectedJob = selectedJob().map(Job::id).orElse(-1);
        if (jobModel.setJobs(allJobs.stream().filter(j -> j.printer().equals(printer)).toList())) {
            int row = jobModel.rowOf(selectedJob);
            if (row >= 0) {
                int view = jobTable.convertRowIndexToView(row);
                jobTable.setRowSelectionInterval(view, view);
            }
        }
        updateActions();
    }

    private void showMessage(String text) {
        message.setText(text);
        cards.show(top, "message");
    }

    private void showError(Throwable error) {
        if (error instanceof IppException e && e.unreachable()) {
            printers.clear();
            allJobs = List.of();
            showJobs();
            showMessage(msg.get("status.no-cups"));
        } else {
            LOG.warn("Asking CUPS failed", error);
            status.setText(msg.format("error.refresh", error.getMessage()));
        }
    }

    private Optional<Printer> selectedPrinter() {
        return Optional.ofNullable(printerList.getSelectedValue());
    }

    private Optional<Job> selectedJob() {
        int row = jobTable.getSelectedRow();
        return row < 0 ? Optional.empty() : Optional.of(jobModel.job(jobTable.convertRowIndexToModel(row)));
    }

    private void updateActions() {
        Optional<Printer> printer = selectedPrinter();
        testPage.setEnabled(printer.isPresent());
        pause.setEnabled(printer.map(p -> p.state() != Printer.State.STOPPED).orElse(false));
        resume.setEnabled(printer.map(p -> p.state() == Printer.State.STOPPED).orElse(false));
        makeDefault.setEnabled(printer.map(p -> !p.isDefault()).orElse(false));
        Optional<Job> job = selectedJob();
        cancel.setEnabled(job.map(Job::cancellable).orElse(false));
        hold.setEnabled(job.map(Job::holdable).orElse(false));
        release.setEnabled(job.map(Job::releasable).orElse(false));
    }

    private interface CupsCall<T> {
        void run(T target) throws IppException;
    }

    private void onPrinter(CupsCall<Printer> call) {
        selectedPrinter().ifPresent(p -> perform(() -> {
            call.run(p);
            return null;
        }));
    }

    private void onJob(CupsCall<Integer> call) {
        selectedJob().ifPresent(j -> perform(() -> {
            call.run(j.id());
            return null;
        }));
    }

    private void cancelJob() {
        selectedJob().filter(Job::cancellable).ifPresent(job -> {
            String name = job.name().isBlank() ? "#" + job.id() : job.name();
            if (JOptionPane.showConfirmDialog(this, msg.format("confirm.cancel", name), getTitle(),
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
                perform(() -> {
                    cups.cancel(job.id());
                    return null;
                });
            }
        });
    }

    /** Runs a CUPS request, then shows the new state; refusals are explained in a dialog. */
    private void perform(Callable<Void> call) {
        background(call, ignored -> refreshSoon(), error -> {
            String text = error instanceof IppException e && e.notAllowed() ? msg.get("error.not-allowed")
                    : msg.format("error.action", error.getMessage());
            JOptionPane.showMessageDialog(this, text, getTitle(), JOptionPane.ERROR_MESSAGE);
            refreshSoon();
        });
    }

    private void refreshSoon() {
        refreshing = false;
        refresh();
    }

    private void printTestPage() {
        selectedPrinter().ifPresent(p -> background(() -> TestPage.print(msg, p.name(), user), sent -> {
            if (sent) {
                status.setText(msg.format("status.test-page", p.title()));
                refreshSoon();
            } else {
                JOptionPane.showMessageDialog(this, msg.format("error.no-service", p.name()), getTitle(),
                        JOptionPane.ERROR_MESSAGE);
            }
        }, error -> JOptionPane.showMessageDialog(this, msg.format("error.action", error.getMessage()), getTitle(),
                JOptionPane.ERROR_MESSAGE)));
    }

    private void openAdministration() {
        try {
            launcher.browse(CUPS_ADMIN);
        } catch (Exception e) {
            LOG.warn("Cannot open {}", CUPS_ADMIN, e);
            JOptionPane.showMessageDialog(this, msg.format("error.browser", CUPS_ADMIN), getTitle(),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private <T> void background(Callable<T> work, Consumer<T> done, Consumer<Throwable> failed) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return work.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor).whenCompleteAsync((result, error) -> {
            if (error == null) {
                done.accept(result);
            } else {
                failed.accept(error instanceof CompletionException && error.getCause() != null ? error.getCause() : error);
            }
        }, edt);
    }

    private Action action(String key, KeyStroke accelerator, Runnable run) {
        Mnemonics.Text text = Mnemonics.parse(msg.get(key));
        Action action = new AbstractAction(text.text()) {
            @Override
            public void actionPerformed(ActionEvent e) {
                run.run();
            }
        };
        if (text.index() >= 0) {
            action.putValue(Action.MNEMONIC_KEY, text.keyCode());
            action.putValue(Action.DISPLAYED_MNEMONIC_INDEX_KEY, text.index());
        }
        if (accelerator != null) action.putValue(Action.ACCELERATOR_KEY, accelerator);
        return action;
    }

    private static JPopupMenu popup(Action... actions) {
        JPopupMenu menu = new JPopupMenu();
        for (Action a : actions) menu.add(a);
        return menu;
    }

    /** Two lines per printer: its name, then state, jobs and whether it is the default. */
    static final class PrinterRenderer extends DefaultListCellRenderer {
        private final Messages msg;
        private final PrinterIcon icon = new PrinterIcon();

        PrinterRenderer(Messages msg) {
            this.msg = msg;
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                      boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof Printer p) {
                setText("<html><b>" + html(p.title()) + "</b><br>" + html(describe(msg, p)) + "</html>");
                setIcon(icon.forState(p.state()));
                setToolTipText(p.makeAndModel().isBlank() ? null : p.makeAndModel());
            }
            setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            return this;
        }
    }

    /** "Ready · 2 jobs · default · Office", and the printer's own message when it has one. */
    static String describe(Messages msg, Printer p) {
        List<String> parts = new ArrayList<>();
        parts.add(msg.get("printer." + p.state().name().toLowerCase(Locale.ROOT)));
        parts.add(msg.format("printer.jobs", p.jobCount()));
        if (p.isDefault()) parts.add(msg.get("printer.default"));
        if (!p.accepting()) parts.add(msg.get("printer.rejecting"));
        if (!p.location().isBlank()) parts.add(p.location());
        if (!p.stateMessage().isBlank()) parts.add(p.stateMessage());
        return String.join(" · ", parts);
    }

    private static String html(String text) {
        return Objects.toString(text, "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
