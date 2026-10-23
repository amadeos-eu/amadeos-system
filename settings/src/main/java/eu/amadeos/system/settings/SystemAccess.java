package eu.amadeos.system.settings;

import eu.amadeos.system.base.process.ProcessLauncher;
import eu.amadeos.system.base.xdg.XdgApplicationManager;
import eu.amadeos.system.base.xdg.XdgDesktopEntry;
import eu.amadeos.system.display.DisplayApplier;
import eu.amadeos.system.keyring.Keyring;
import eu.amadeos.system.keyring.SecretServiceKeyring;
import eu.amadeos.system.packages.AptPackageService;
import eu.amadeos.system.packages.PackageService;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * What the System pages of the Settings reach beyond files of the user: the login keyring, the package
 * manager, the computer's time zone, the monitors (through xrandr), and starting other programs. Tests give the window stand-ins.
 *
 * @param launcher     starts a program of the desktop, e.g. {@code amadeos-software --updates}
 * @param applications the installed programs, as the Front Panel's drawers list them
 */
record SystemAccess(Keyring keyring, PackageService packages, TimeZones timeZones, Consumer<List<String>> launcher,
                    Supplier<List<XdgDesktopEntry>> applications, DisplayApplier displays) {
    private static final Logger LOGGER = Logger.getLogger(SystemAccess.class.getName());

    static SystemAccess system() {
        return new SystemAccess(new SecretServiceKeyring(), new AptPackageService(), TimeZones.system(), command -> {
            try {
                ProcessLauncher.start(command);
            } catch (IOException e) {
                LOGGER.warning("Cannot start " + command + ": " + e.getMessage());
            }
        }, () -> XdgApplicationManager.getInstance().getAllApplications(), new DisplayApplier());
    }
}
