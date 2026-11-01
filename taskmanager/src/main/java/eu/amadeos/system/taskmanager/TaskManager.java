package eu.amadeos.system.taskmanager;

import eu.amadeos.commons.i18n.LocaleResolution;
import eu.amadeos.commons.i18n.LocaleStore;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.system.base.process.InstanceChannel;
import eu.amadeos.commons.theme.ThemeStore;
import eu.amadeos.system.taskmanager.control.ProcessControl;
import eu.amadeos.system.taskmanager.model.Sampler;
import eu.amadeos.system.taskmanager.proc.ProcFs;
import eu.amadeos.system.taskmanager.ui.Context;
import eu.amadeos.system.taskmanager.ui.MainWindow;
import eu.amadeos.system.taskmanager.ui.Monitor;
import eu.amadeos.system.taskmanager.ui.NotRespondingDialog;
import eu.amadeos.system.taskmanager.x.WindowInfo;
import eu.amadeos.system.taskmanager.x.Windows;
import eu.amadeos.system.taskmanager.x.X11Windows;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Task Manager, {@code amadeos-taskmanager}: programs, processes and how busy the computer is.
 *
 * <pre>
 * amadeos-taskmanager                       the window (Ctrl+Shift+Esc, Ctrl+Alt+Delete, the Tools drawer)
 * amadeos-taskmanager --processes           the window, on the Processes tab
 * amadeos-taskmanager --performance         the window, on the Performance tab
 * amadeos-taskmanager --not-responding WIN  the question for a window that does not answer (the window manager)
 * </pre>
 *
 * A second start hands its arguments to the running Task Manager ({@link InstanceChannel}). The program ends
 * when its last window closes.
 */
public final class TaskManager {
    private static final Logger LOGGER = Logger.getLogger(TaskManager.class.getName());
    static final String TEXTS = "eu.amadeos.system.taskmanager.messages";

    private final Context context;
    private MainWindow main;
    private NotRespondingDialog hang;

    private TaskManager(Messages texts, Monitor monitor, int ownUid) {
        ProcessControl control = new ProcessControl(ProcessControl.Runner.SYSTEM,
                pid -> monitor.sampler().proc().process(pid).map(p -> p.uid() == ownUid).orElse(true));
        this.context = new Context(texts, monitor, control, ownUid, this::showProcess, this::showProgram);
    }

    public static void main(String[] args) {
        List<String> arguments = Arrays.asList(args);
        if (arguments.contains("--help")) {
            System.out.println("Usage: amadeos-taskmanager [--processes | --performance | --not-responding WINDOW]");
            return;
        }
        if (InstanceChannel.send(InstanceChannel.defaultPath("amadeos-taskmanager"), arguments)) return;
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("amadeos-taskmanager: no X display");
            System.exit(5);
        }
        Locale locale = LocaleResolution.resolve(Optional.empty(), LocaleStore.system().current(), System.getenv())
                .language().locale();
        Messages texts = Messages.load(TaskManager.class, TEXTS, locale);
        ProcFs proc = ProcFs.system();
        int ownUid = proc.process((int) ProcessHandle.current().pid()).map(p -> p.uid()).orElse(-1);
        long ownPid = ProcessHandle.current().pid();
        String title = texts.get("app.title");
        // The Task Manager lists its own main window, as one program, but not its dialogs.
        Windows windows = X11Windows.open(w -> w.pid() == ownPid && !w.title().equals(title));
        Monitor monitor = new Monitor(new Sampler(proc, System::nanoTime), windows);
        TaskManager app = new TaskManager(texts, monitor, ownUid);
        ThemeStore theme = ThemeStore.system();
        try {
            SwingUtilities.invokeAndWait(() -> {
                AmadeUI.install(theme.current());
                AmadeUI.follow(theme);
            });
            InstanceChannel.listen(InstanceChannel.defaultPath("amadeos-taskmanager"),
                    a -> SwingUtilities.invokeLater(() -> app.handle(a)));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Another start cannot reach this Task Manager", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Cannot set up the user interface", e);
            System.exit(5);
        }
        monitor.setInterval(1);
        SwingUtilities.invokeLater(() -> app.handle(arguments));
    }

    private void handle(List<String> arguments) {
        int i = arguments.indexOf("--not-responding");
        if (i >= 0 && i + 1 < arguments.size()) {
            try {
                notResponding(Long.decode(arguments.get(i + 1)));
            } catch (NumberFormatException e) {
                LOGGER.warning(() -> "Not a window id: " + arguments.get(i + 1));
                if (main == null) System.exit(2);
            }
            return;
        }
        MainWindow window = window();
        if (arguments.contains("--processes")) window.showTab(1);
        if (arguments.contains("--performance")) window.showTab(2);
        window.toFront();
    }

    /** The main window, made visible (created if needed). */
    private MainWindow window() {
        if (main == null || !main.isDisplayable()) {
            main = new MainWindow(context);
            watch(main);
        }
        main.setVisible(true);
        main.setExtendedState(main.getExtendedState() & ~java.awt.Frame.ICONIFIED);
        return main;
    }

    private void showProcess(int pid) {
        window().showProcess(pid);
    }

    private void showProgram(long windowId) {
        window().showProgram(windowId);
    }

    /** Asks about a window that does not answer, once at a time. */
    private void notResponding(long windowId) {
        if (hang != null && hang.isDisplayable() && hang.windowId() == windowId) {
            hang.toFront();
            return;
        }
        CompletableFuture<Optional<WindowInfo>> found = new CompletableFuture<>();
        context.monitor().onWorker(w -> found.complete(w.find(windowId)));
        found.thenAccept(window -> SwingUtilities.invokeLater(() -> {
            if (window.isEmpty() || !window.get().notResponding()) {
                LOGGER.info(() -> "Window " + windowId + " answers again or is gone");
                exitIfLast();
                return;
            }
            if (hang != null) hang.dispose();
            hang = new NotRespondingDialog(context, window.get());
            watch(hang);
            hang.setVisible(true);
        }));
    }

    private void watch(Window w) {
        w.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                SwingUtilities.invokeLater(TaskManager.this::exitIfLast);
            }
        });
    }

    /** Ends the program when none of its windows is open any more. */
    private void exitIfLast() {
        for (Window w : Window.getWindows()) {
            if (w.isDisplayable() && w.isVisible()) return;
        }
        context.monitor().shutdown();
        System.exit(0);
    }
}
