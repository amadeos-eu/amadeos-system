package eu.amadeos.system.settings;

import eu.amadeos.commons.net.ProxyCheck;
import eu.amadeos.commons.net.ProxySettings;
import eu.amadeos.system.base.net.NetworkService;
import eu.amadeos.system.base.net.NmcliNetworkService;
import eu.amadeos.system.base.power.BatteryStatus;
import eu.amadeos.system.base.power.LogindPowerControl;
import eu.amadeos.system.base.power.PowerControl;
import eu.amadeos.system.base.power.PowerSupplies;
import eu.amadeos.system.base.process.ProcessLauncher;
import eu.amadeos.system.base.sound.PactlSoundService;
import eu.amadeos.system.base.sound.SoundService;
import eu.amadeos.system.base.bluetooth.BluetoothService;
import eu.amadeos.system.base.bluetooth.BluezBluetoothService;
import eu.amadeos.system.base.xdg.XdgApplicationManager;
import eu.amadeos.system.base.xdg.XdgDesktopEntry;
import eu.amadeos.system.display.DisplayApplier;
import eu.amadeos.system.keyring.Keyring;
import eu.amadeos.system.keyring.SecretServiceKeyring;
import eu.amadeos.system.packages.AptPackageService;
import eu.amadeos.system.packages.PackageService;
import eu.amadeos.system.printers.CupsPrinterService;
import eu.amadeos.system.printers.PrinterService;
import eu.amadeos.system.users.SystemUsers;
import eu.amadeos.system.users.UserService;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * What the System pages of the Settings reach beyond files of the user: the login keyring, the package
 * manager, the computer's time zone, the monitors (through xrandr), starting other programs, trying a proxy,
 * NetworkManager, the sound server, BlueZ, CUPS, the accounts of the computer, logind and the battery. Tests give the window stand-ins.
 *
 * @param launcher     starts a program of the desktop, e.g. {@code amadeos-software --updates}
 * @param applications the installed programs, as the Front Panel's drawers list them
 */
record SystemAccess(Keyring keyring, PackageService packages, TimeZones timeZones, Consumer<List<String>> launcher,
                    Supplier<List<XdgDesktopEntry>> applications, DisplayApplier displays,
                    BiFunction<ProxySettings, String, ProxyCheck.Result> proxyCheck, NetworkService network,
                    SoundService sound, SoundCheck soundTest, BluetoothService bluetooth, PrinterService printers,
                    UserService users, PowerControl power, Supplier<Optional<BatteryStatus>> battery) {
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
                        SettingsTexts.get("printers.testpage.title")), new SystemUsers(), new LogindPowerControl(),
                new PowerSupplies()::battery);
    }
}
