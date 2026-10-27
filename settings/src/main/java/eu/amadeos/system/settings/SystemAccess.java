package eu.amadeos.system.settings;

import eu.amadeos.commons.net.ProxyCheck;
import eu.amadeos.commons.net.ProxySettings;
import eu.amadeos.system.base.net.NetworkService;
import eu.amadeos.system.base.net.NmcliNetworkService;
import eu.amadeos.system.base.process.ProcessLauncher;
import eu.amadeos.system.base.sound.PactlSoundService;
import eu.amadeos.system.base.sound.SoundService;
import eu.amadeos.system.bluetooth.BluetoothService;
import eu.amadeos.system.bluetooth.BluezBluetoothService;
import eu.amadeos.system.base.xdg.XdgApplicationManager;
import eu.amadeos.system.base.xdg.XdgDesktopEntry;
import eu.amadeos.system.display.DisplayApplier;
import eu.amadeos.system.keyring.Keyring;
import eu.amadeos.system.keyring.SecretServiceKeyring;
import eu.amadeos.system.packages.AptPackageService;
import eu.amadeos.system.packages.PackageService;
import eu.amadeos.system.printers.CupsPrinterService;
import eu.amadeos.system.printers.PrinterService;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * What the System pages of the Settings reach beyond files of the user: the login keyring, the package
 * manager, the computer's time zone, the monitors (through xrandr), starting other programs, trying a proxy,
 * NetworkManager, the sound server, BlueZ and CUPS. Tests give the window stand-ins.
 *
 * @param launcher     starts a program of the desktop, e.g. {@code amadeos-software --updates}
 * @param applications the installed programs, as the Front Panel's drawers list them
 */
record SystemAccess(Keyring keyring, PackageService packages, TimeZones timeZones, Consumer<List<String>> launcher,
                    Supplier<List<XdgDesktopEntry>> applications, DisplayApplier displays,
                    BiFunction<ProxySettings, String, ProxyCheck.Result> proxyCheck, NetworkService network,
                    SoundService sound, SoundCheck soundTest, BluetoothService bluetooth, PrinterService printers) {
    private static final Logger LOGGER = Logger.getLogger(SystemAccess.class.getName());

    static SystemAccess system() {
        return new SystemAccess(new SecretServiceKeyring(), new AptPackageService(), TimeZones.system(), command -> {
            try {
                ProcessLauncher.start(command);
            } catch (IOException e) {
                LOGGER.warning("Cannot start " + command + ": " + e.getMessage());
            }
        }, () -> XdgApplicationManager.getInstance().getAllApplications(), new DisplayApplier(),
                (settings, password) -> new ProxyCheck().check(settings, password), new NmcliNetworkService(),
                new PactlSoundService(), new PulseSoundCheck(), new BluezBluetoothService(),
                new CupsPrinterService(System.getProperty("user.name", ""), Locale.getDefault(),
                        SettingsTexts.get("printers.testpage.title")));
    }
}
