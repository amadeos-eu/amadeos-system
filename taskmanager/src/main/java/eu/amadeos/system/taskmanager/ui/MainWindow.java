package eu.amadeos.system.taskmanager.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.system.base.process.ProcessLauncher;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;

/** The Task Manager's window: the tabs Programs, Processes and Performance, and a status line. */
public final class MainWindow extends JFrame {
    /** The chapter of the User's Handbook, in the package amadeos-handbook and on the web. */
    private static final String HELP = "/usr/share/doc/amadeos-handbook/html/59-task-manager.html";
    private static final String HELP_ONLINE = "https://docs.amadeos.eu/amadeos/59-task-manager/";

    private final Context context;
    private final Messages t;
    private final JTabbedPane tabs = new JTabbedPane();
    private final ProgramsPanel programs;
    private final ProcessesPanel processes;
    private final PerformancePanel performance;
    private final JLabel status = new JLabel(" ");
    private final JRadioButtonMenuItem[] speeds = new JRadioButtonMenuItem[PerformancePanel.SPEEDS.length];

    public MainWindow(Context context) {
        super(context.texts().get("app.title"));
        this.context = context;
        this.t = context.texts();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        programs = new ProgramsPanel(context);
        processes = new ProcessesPanel(context);
        performance = new PerformancePanel(context, this::setSpeed);

        tabs.addTab(t.get("tab.programs"), programs);
        tabs.addTab(t.get("tab.processes"), processes);
        tabs.addTab(t.get("tab.performance"), performance);
        tabs.setMnemonicAt(0, KeyEvent.VK_1);
        JPanel centre = new JPanel(new BorderLayout());
        centre.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
        centre.add(tabs);
        add(centre, BorderLayout.CENTER);
        status.setBorder(BorderFactory.createCompoundBorder(eu.amadeos.commons.swing.AmadeUI.sunken(),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
        bottom.add(status);
        add(bottom, BorderLayout.SOUTH);
        setJMenuBar(menus());
        for (int i = 0; i < 3; i++) {
            int tab = i;
            tabs.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, KeyEvent.CTRL_DOWN_MASK), "tab" + i);
            tabs.getActionMap().put("tab" + i, new javax.swing.AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    tabs.setSelectedIndex(tab);
                }
            });
        }
        getRootPane().registerKeyboardAction(e -> context.monitor().refresh(),
                KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        context.monitor().addListener(this::show);
        if (context.monitor().last() != null) show(context.monitor().last());
        setSize(new Dimension(860, 560));
        setLocationRelativeTo(null);
    }

    private JMenuBar menus() {
        JMenuBar bar = new JMenuBar();
        JMenu file = Mnemonics.apply(new JMenu(), t.get("menu.file"));
        JMenuItem run = Mnemonics.apply(new JMenuItem(), t.get("menu.file.new-task"));
        run.addActionListener(e -> runProgram());
        JMenuItem close = Mnemonics.apply(new JMenuItem(), t.get("menu.file.close"));
        close.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.CTRL_DOWN_MASK));
        close.addActionListener(e -> dispose());
        file.add(run);
        file.addSeparator();
        file.add(close);
        bar.add(file);

        JMenu process = Mnemonics.apply(new JMenu(), t.get("menu.process"));
        process.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                process.removeAll();
                for (java.awt.Component c : processes.menuItems()) process.add(c);
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });
        bar.add(process);

        JMenu view = Mnemonics.apply(new JMenu(), t.get("menu.view"));
        JCheckBoxMenuItem tree = Mnemonics.apply(new JCheckBoxMenuItem(), t.get("menu.view.tree"));
        tree.setModel(processes.treeBox().getModel());
        JCheckBoxMenuItem all = Mnemonics.apply(new JCheckBoxMenuItem(), t.get("menu.view.all-users"));
        all.setModel(processes.allUsersBox().getModel());
        JMenu speed = Mnemonics.apply(new JMenu(), t.get("menu.view.update"));
        ButtonGroup group = new ButtonGroup();
        String[] keys = {"update.1", "update.2", "update.5", "update.paused"};
        for (int i = 0; i < keys.length; i++) {
            int index = i;
            speeds[i] = new JRadioButtonMenuItem(t.get(keys[i]), i == 0);
            speeds[i].addActionListener(e -> performance.speedBox().setSelectedIndex(index));
            group.add(speeds[i]);
            speed.add(speeds[i]);
        }
        JMenuItem refresh = Mnemonics.apply(new JMenuItem(), t.get("menu.view.refresh"));
        refresh.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        refresh.addActionListener(e -> context.monitor().refresh());
        view.add(tree);
        view.add(all);
        view.addSeparator();
        view.add(speed);
        view.add(refresh);
        bar.add(view);

        JMenu help = Mnemonics.apply(new JMenu(), t.get("menu.help"));
        JMenuItem contents = Mnemonics.apply(new JMenuItem(), t.get("menu.help.contents"));
        contents.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
        contents.addActionListener(e -> launch(List.of("xdg-open", new File(HELP).isFile() ? HELP : HELP_ONLINE)));
        JMenuItem about = Mnemonics.apply(new JMenuItem(), t.get("menu.help.about"));
        about.addActionListener(e -> JOptionPane.showMessageDialog(this, t.get("about.text"), t.get("app.title"),
                JOptionPane.INFORMATION_MESSAGE));
        help.add(contents);
        help.addSeparator();
        help.add(about);
        bar.add(help);
        return bar;
    }

    private void setSpeed(int seconds) {
        for (int i = 0; i < PerformancePanel.SPEEDS.length; i++) {
            if (PerformancePanel.SPEEDS[i] == seconds) speeds[i].setSelected(true);
        }
        context.monitor().setInterval(seconds);
    }

    private void show(Snapshot s) {
        if (!isDisplayable()) return;
        programs.show(s);
        processes.show(s);
        performance.show(s, context.monitor().sampler());
        var m = s.sample().memory();
        var l = context.locale();
        status.setText(t.format("bar.status", Integer.toString(s.sample().processes().size()),
                Units.percent(s.sample().cpu(), l), Units.bytes(m.used(), l), Units.bytes(m.total(), l),
                Units.rate(s.sample().netDown(), l), Units.rate(s.sample().netUp(), l)));
    }

    /** Shows the Processes tab with the process selected. */
    public void showProcess(int pid) {
        tabs.setSelectedIndex(1);
        processes.select(pid);
        toFront();
    }

    /** Shows the Programs tab with the window's line selected. */
    public void showProgram(long windowId) {
        tabs.setSelectedIndex(0);
        programs.select(windowId);
        toFront();
    }

    /** Selects a tab: 0 Programs, 1 Processes, 2 Performance. */
    public void showTab(int index) {
        tabs.setSelectedIndex(index);
    }

    private void runProgram() {
        String command = (String) JOptionPane.showInputDialog(this, t.get("run.prompt"), t.get("run.title"),
                JOptionPane.PLAIN_MESSAGE, null, null, "");
        if (command == null || command.isBlank()) return;
        launch(List.of("sh", "-c", command.strip()));
    }

    private void launch(List<String> command) {
        try {
            ProcessLauncher.start(command);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, t.format("run.failed", String.join(" ", command), e.getMessage()),
                    t.get("error.title"), JOptionPane.ERROR_MESSAGE);
        }
    }

    // For the tests and the pictures of the handbook.
    ProgramsPanel programsPanel() {
        return programs;
    }

    ProcessesPanel processesPanel() {
        return processes;
    }
}
