package eu.amadeos.system.disks;

import eu.amadeos.commons.i18n.LocaleResolution;
import eu.amadeos.commons.i18n.LocaleStore;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.ThemeStore;
import eu.amadeos.system.base.process.InstanceChannel;
import eu.amadeos.system.disks.service.DiskService;
import eu.amadeos.system.disks.udisks.UDisksService;
import eu.amadeos.system.disks.ui.Context;
import eu.amadeos.system.disks.ui.MainWindow;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Disks, {@code amadeos-disks}: the drives of the computer, their partitions and health.
 *
 * <pre>
 * amadeos-disks                  the window (the Tools drawer)
 * amadeos-disks --drive /dev/sdb the window with that drive chosen
 * amadeos-disks --format /dev/sdb the window, asking how to format that drive (Files: Format…)
 * </pre>
 *
 * A second start hands its arguments to the running Disks ({@link InstanceChannel}).
 */
public final class Disks {
    private static final Logger LOGGER = Logger.getLogger(Disks.class.getName());
    public static final String TEXTS = "eu.amadeos.system.disks.messages";
    static final String CHANNEL = "amadeos-disks";

    private final Context context;
    private MainWindow window;
    private AutoCloseable watch;

    private Disks(Context context) {
        this.context = context;
    }

    public static void main(String[] args) {
        List<String> arguments = Arrays.asList(args);
        if (arguments.contains("--help")) {
            System.out.println("Usage: amadeos-disks [--drive DEVICE | --format DEVICE]");
            return;
        }
        if (InstanceChannel.send(InstanceChannel.defaultPath(CHANNEL), arguments)) return;
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("amadeos-disks: no X display");
            System.exit(5);
        }
        Locale locale = LocaleResolution.resolve(Optional.empty(), LocaleStore.system().current(), System.getenv())
                .language().locale();
        Messages texts = Messages.load(Disks.class, TEXTS, locale);
        DiskService service = new UDisksService();
        Disks app = new Disks(new Context(texts, service, System.getProperty("user.name")));
        ThemeStore theme = ThemeStore.system();
        try {
            SwingUtilities.invokeAndWait(() -> {
                AmadeUI.install(theme.current());
                AmadeUI.follow(theme);
            });
            InstanceChannel.listen(InstanceChannel.defaultPath(CHANNEL),
                    a -> SwingUtilities.invokeLater(() -> app.handle(a)));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Another start cannot reach this Disks window", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Cannot set up the user interface", e);
            System.exit(5);
        }
        SwingUtilities.invokeLater(() -> app.handle(arguments));
    }

    private void handle(List<String> arguments) {
        if (window == null || !window.isDisplayable()) {
            window = new MainWindow(context);
            window.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    try {
                        if (watch != null) watch.close();
                    } catch (Exception ex) {
                        LOGGER.log(Level.FINE, "Stop following udisks", ex);
                    }
                    System.exit(0);
                }
            });
            MainWindow w = window;
            watch = context.service().watch(w::refreshSoon);
            window.refresh();
        }
        int format = arguments.indexOf("--format");
        int drive = arguments.indexOf("--drive");
        if (format >= 0 && format + 1 < arguments.size()) window.select(arguments.get(format + 1), true);
        else if (drive >= 0 && drive + 1 < arguments.size()) window.select(arguments.get(drive + 1), false);
        window.setVisible(true);
        window.setExtendedState(window.getExtendedState() & ~java.awt.Frame.ICONIFIED);
        window.toFront();
    }
}
