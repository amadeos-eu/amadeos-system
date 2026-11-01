package eu.amadeos.system.settings;

import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleStore;
import eu.amadeos.commons.i18n.RegionalFormats;
import eu.amadeos.commons.net.ProxyCheck;
import eu.amadeos.commons.net.ProxySettings;
import eu.amadeos.system.base.panel.PanelSettings;
import eu.amadeos.system.base.panel.PanelSettingsStore;
import eu.amadeos.system.base.startup.StartupSettings;
import eu.amadeos.system.base.startup.UserAutostart;
import eu.amadeos.system.base.workspace.WorkspaceNames;
import eu.amadeos.system.base.xdg.MimeApps;
import eu.amadeos.system.base.xdg.XdgDesktopEntry;
import eu.amadeos.system.base.xdg.XdgDesktopEntryParser;
import eu.amadeos.system.keyring.Keyring;
import eu.amadeos.system.packages.Change;
import eu.amadeos.system.packages.Origin;
import eu.amadeos.system.packages.PackageService;
import eu.amadeos.system.packages.Result;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The pages added after Font and Window, with stand-ins for the keyring, apt and timedatectl. */
class SystemPagesTest {
    @TempDir
    Path dir;

    @BeforeAll
    static void english() {
        SettingsTexts.use(Locale.ENGLISH);
    }

    /** A system without a keyring, with an apt that has nothing to offer, and a time zone that only remembers. */
    static SystemAccess fakeSystem() {
        return new SystemAccess(new FakeKeyring(), packages(new ArrayList<>(), List.of()), new FakeClock(),
                command -> { }, List::of, new FakeDisplays(),
                (settings, password) -> new ProxyCheck.Result(ProxyCheck.Outcome.OK, "", "200"),
                new FakeDevices.Network(), new FakeDevices.Sound(), new FakeDevices.Check(), new FakeDevices.Bluetooth(),
                new FakeDevices.Printers(), new FakeUsers(), new FakePower(), FakePower::laptopBattery, SystemPagesTest::fakeMimeApps,
                () -> RegionalFormats.REGIONS);
    }

    /** An apt whose automatic security updates are off and which records the calls that change them. */
    static PackageService packages(List<Boolean> calls, List<Change> upgrades) {
        return (PackageService) Proxy.newProxyInstance(SystemPagesTest.class.getClassLoader(),
                new Class<?>[]{PackageService.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "automaticSecurityUpdates" -> calls.isEmpty() ? false : calls.getLast();
                    case "upgrades" -> upgrades;
                    case "lastRefresh" -> Optional.empty();
                    case "setAutomaticSecurityUpdates" -> {
                        calls.add((Boolean) args[0]);
                        yield new Result(Result.Status.DONE, "", false);
                    }
                    case "toString" -> "fake apt";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void workspacesPageRenamesAndGivesDefaultNamesBack() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        WorkspaceNames names = new WorkspaceNames(dir);
        names.rename(1, "Mail");
        AtomicReference<Boolean> backdrop = new AtomicReference<>(false);
        SwingUtilities.invokeAndWait(() -> {
            WorkspacesPage page = new WorkspacesPage(s -> { }, names, () -> backdrop.set(true));
            assertEquals("One", page.field(0).getText(), "a workspace without a name shows its own");
            assertEquals("Mail", page.field(1).getText());
            page.field(0).setText("  Writing\t");
            page.field(1).setText("");
            page.field(2).setText("Three");
            assertEquals("Workspaces applied", page.apply());
            assertEquals(Optional.of("Writing"), names.custom(0));
            assertEquals(Optional.empty(), names.custom(1), "an empty name gives the default back");
            assertEquals(Optional.empty(), names.custom(2), "the default name is no name of its own");
            assertEquals("Two", page.field(1).getText());
            findButton(page, "Backdrops…").doClick();
            assertTrue(backdrop.get());
        });
    }

    @Test
    void panelPageWritesWhatThePanelFollows() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        PanelSettingsStore store = new PanelSettingsStore(dir.resolve(PanelSettingsStore.FILE_NAME));
        SwingUtilities.invokeAndWait(() -> {
            PanelPage page = new PanelPage(s -> { }, store);
            assertEquals(PanelSettings.DEFAULTS, page.selected());
            findButton(page, "Digital: time and date in figures").doClick();
            findButton(page, "Network").doClick();
            findButton(page, "Show disk and network activity").doClick();
            assertEquals("Front Panel applied", page.apply());
        });
        assertEquals(new PanelSettings(PanelSettings.Clock.DIGITAL, false, true, true, true, true, false, true), store.load());
    }

    @Test
    void startupPageKeepsRequiredServicesAndAddsPrograms() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path services = Files.createDirectory(dir.resolve("autostart.d"));
        for (String name : List.of("30-amadeos-polkit-agent", "40-amadeos-clipboard", "60-extra")) {
            Files.setPosixFilePermissions(Files.writeString(services.resolve(name), "#!/bin/sh\n"),
                    PosixFilePermissions.fromString("rwxr-xr-x"));
        }
        Path pad = Files.writeString(Files.createDirectory(dir.resolve("apps")).resolve("amade-pad.desktop"),
                "[Desktop Entry]\nType=Application\nName=Text Editor\nExec=sh\n");
        XdgDesktopEntry editor = XdgDesktopEntryParser.parse(pad).orElseThrow();
        StartupSettings settings = new StartupSettings(dir.resolve(StartupSettings.FILE_NAME));
        settings.saveDisabled(Set.of("50-uninstalled"));
        UserAutostart autostart = new UserAutostart(dir.resolve("autostart"));
        SwingUtilities.invokeAndWait(() -> {
            StartupPage page = new StartupPage(s -> { }, settings, autostart, services, () -> List.of(editor));
            JCheckBox polkit = page.services().get("30-amadeos-polkit-agent");
            assertEquals("Asking for the administrator's password", polkit.getText());
            assertFalse(polkit.isEnabled(), "the desktop needs it");
            assertEquals("60-extra", page.services().get("60-extra").getText(), "unknown scripts by their name");
            page.services().get("40-amadeos-clipboard").setSelected(false);
            page.programs().addElement(editor);
            assertEquals("Startup applied; it takes effect at the next login", page.apply());
        });
        assertEquals(Set.of("40-amadeos-clipboard", "50-uninstalled"), settings.disabled(),
                "switched off now, and kept for a script that is not installed");
        assertEquals(List.of("Text Editor"), autostart.entries().stream().map(XdgDesktopEntry::name).toList());
    }

    @Test
    void passwordsPageShowsTheKeyringAndLocksIt() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        FakeKeyring keyring = new FakeKeyring();
        keyring.status = new Keyring.Status(true, false, 3);
        AtomicReference<PasswordsPage> page = new AtomicReference<>();
        List<String> lines = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> page.set(new PasswordsPage(lines::add, keyring)));
        waitFor(() -> page.get().shown().available());
        SwingUtilities.invokeAndWait(() -> {
            assertFalse(findButton(page.get(), "Unlock…").isEnabled());
            findButton(page.get(), "Lock Now").doClick();
        });
        waitFor(() -> page.get().shown().locked());
        assertTrue(keyring.locked);
        SwingUtilities.invokeAndWait(() -> assertEquals(List.of("The keyring is locked"), lines));
    }

    @Test
    void updatesPageChangesTheAutomaticSecurityUpdates() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        List<Boolean> calls = new ArrayList<>();
        Change openssl = new Change(Change.Kind.UPGRADE, "openssl", "3.0.1", "3.0.2",
                List.of(new Origin("Debian", "13", "trixie-security")), "amd64");
        PackageService apt = packages(calls, List.of(openssl));
        AtomicReference<UpdatesPage> page = new AtomicReference<>();
        List<String> lines = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> page.set(new UpdatesPage(lines::add, apt, c -> { })));
        waitFor(() -> page.get().automaticBox().isEnabled());
        SwingUtilities.invokeAndWait(() -> {
            assertFalse(page.get().automaticBox().isSelected());
            page.get().automaticBox().setSelected(true);
            assertEquals("Updates: saving…", page.get().apply());
        });
        waitFor(() -> !calls.isEmpty());
        assertEquals(List.of(true), calls);
        assertEquals("1 update, 1 of them for security", UpdatesPage.describe(List.of(openssl)));
        assertEquals("no updates, the system is up to date", UpdatesPage.describe(List.of()));
    }

    @Test
    void networkPageWritesTheProxyAndHandsItToApt() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        SettingsStores stores = SettingsStores.in(dir);
        List<String> aptCalls = new ArrayList<>();
        PackageService apt = (PackageService) Proxy.newProxyInstance(SystemPagesTest.class.getClassLoader(),
                new Class<?>[]{PackageService.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "setAptProxy" -> {
                        aptCalls.add(args[0] + " " + args[1]);
                        yield new Result(Result.Status.DONE, "", false);
                    }
                    case "toString" -> "fake apt";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        List<ProxySettings> tested = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        AtomicReference<NetworkPage> page = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            page.set(new NetworkPage(lines::add, stores.proxy(), apt, stores, (s, pw) -> {
                tested.add(s);
                return new ProxyCheck.Result(ProxyCheck.Outcome.CREDENTIALS_REFUSED, "proxy.example:3128", "407");
            }, Map.of("https_proxy", "http://env.example:8080"), new FakeDevices.Network()));
            assertEquals("Now: env.example:8080", page.get().describeEnvironment());
            assertEquals(ProxySettings.Mode.SYSTEM, page.get().selected().mode());
            assertFalse(page.get().hostField().isEnabled(), "the manual fields wait for Manual");
            findButton(page.get(), "Use this proxy:").doClick();
            page.get().hostField().setText(" proxy.example ");
            page.get().userField().setText("ralf");
            page.get().passwordField().setText("p@ss");
            findButton(page.get(), "Test").doClick();
        });
        waitFor(() -> page.get().testResult().getText().contains("refused"));
        assertEquals("proxy.example", tested.getFirst().host());

        SwingUtilities.invokeAndWait(() -> {
            page.get().aptBox().setSelected(true);
            assertEquals("Network: saving…", page.get().apply());
        });
        waitFor(() -> !aptCalls.isEmpty());
        assertEquals(List.of("http://ralf:p%40ss@proxy.example:3128 http://ralf:p%40ss@proxy.example:3128"), aptCalls);
        assertEquals(ProxySettings.manual("proxy.example", 3128, "ralf", ProxySettings.DEFAULT_NO_PROXY),
                stores.proxy().current());
        assertEquals("p@ss", stores.proxy().password());
        assertTrue(stores.aptProxy());
        waitFor(() -> lines.contains("Network applied, also for software"));

        SwingUtilities.invokeAndWait(() -> page.get().apply());
        waitFor(() -> lines.size() > 1);
        assertEquals(1, aptCalls.size(), "unchanged settings do not ask for the administrator's password again");

        SwingUtilities.invokeAndWait(() -> {
            findButton(page.get(), "No proxy, always connect directly").doClick();
            page.get().aptBox().setSelected(false);
            page.get().apply();
        });
        waitFor(() -> aptCalls.size() == 2);
        assertEquals(" ", aptCalls.getLast());
        assertEquals(ProxySettings.Mode.NONE, stores.proxy().current().mode());
        assertEquals("", stores.proxy().password(), "no password kept without a manual proxy");
    }

    @Test
    void languagePageWritesTheLanguage() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        LocaleStore locales = new LocaleStore(dir);
        AtomicReference<LanguagePage> page = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            page.set(new LanguagePage(s -> { }, locales));
            assertEquals(Optional.of(Language.ENGLISH), page.get().selectedLanguage());
            findButton(page.get(), "Deutsch").doClick();
            assertEquals("Language: Deutsch", page.get().apply());
        });
        assertEquals(Optional.of(Language.GERMAN), locales.language());
    }

    @Test
    void dateTimePageSetsZoneNetworkTimeClockAndFormats() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        FakeClock clock = new FakeClock();
        RegionalFormats formats = new RegionalFormats(dir);
        AtomicReference<DateTimePage> page = new AtomicReference<>();
        AtomicReference<String> status = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            page.set(new DateTimePage(status::set, clock, formats, new LocaleStore(dir), RegionalFormats.REGIONS));
            assertEquals("Date and time: nothing changed", page.get().apply());
            page.get().zoneBox().setSelectedItem("Europe/Rome");
            page.get().regionBox().setSelectedItem(Locale.of("de", "AT"));
            page.get().ntpBox().doClick();
            page.get().enterTime(LocalDateTime.of(2026, 12, 24, 18, 30, 5));
            assertEquals("Formats: Deutsch (Österreich). Clock: saving…", page.get().apply());
        });
        assertEquals(Optional.of(Locale.of("de", "AT")), formats.region());
        waitFor(() -> "Clock: Europe / Rome, set by hand".equals(status.get()));
        assertEquals(List.of("zone Europe/Rome", "ntp false", "time 2026-12-24T18:30:05"), clock.calls);
        assertTrue(SystemClock.zones().contains("America/New_York"));
        assertFalse(SystemClock.zones().contains("US/Eastern"), "no old aliases");
    }

    @Test
    void timedatectlShowIsParsed() {
        SystemClock.Status s = SystemClock.Timedatectl.parse("""
                Timezone=Europe/Berlin
                LocalRTC=no
                CanNTP=yes
                NTP=yes
                NTPSynchronized=no
                """, "UTC");
        assertEquals(new SystemClock.Status("Europe/Berlin", true, true, false), s);
        assertEquals("UTC", SystemClock.Timedatectl.parse("", "UTC").zone());
    }

    @Test
    void applicationsPageWritesTheUsersChoicesAndDropsThemForTheDefaults() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        MimeApps apps = mimeApps(dir);
        AtomicReference<ApplicationsPage> page = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            page.set(new ApplicationsPage(s -> { }, apps, FakeApplications.ALL));
            javax.swing.JComboBox<String> pictures = page.get().choice(ApplicationsPage.Role.PICTURES);
            assertEquals("amade-imageviewer.desktop", pictures.getSelectedItem());
            assertEquals(List.of("amade-imageeditor.desktop", "amade-imageviewer.desktop", "amade-imagescan.desktop"),
                    page.get().candidates(ApplicationsPage.Role.PICTURES));
            pictures.setSelectedItem("amade-imageeditor.desktop");
            assertEquals("1 default application changed", page.get().apply());
        });
        assertEquals(Optional.of("amade-imageeditor.desktop"), apps.defaultFor("image/png"));
        assertTrue(apps.userChoice("image/webp"));
        assertFalse(apps.userChoice("image/x-bmp"), "the editor does not list it");
        SwingUtilities.invokeAndWait(() -> {
            page.get().useSystemDefaults();
            assertEquals("1 default application changed", page.get().apply());
        });
        assertFalse(apps.userChoice("image/png"));
    }

    /** The default applications of amadeOS's image in a directory of its own, for fake systems. */
    static MimeApps fakeMimeApps() {
        try {
            return mimeApps(java.nio.file.Files.createTempDirectory("mimeapps"));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    static MimeApps mimeApps(Path dir) {
        try {
            Path system = dir.resolve("etc-mimeapps.list");
            java.nio.file.Files.writeString(system, """
                    [Default Applications]
                    x-scheme-handler/http=firefox-esr.desktop
                    x-scheme-handler/https=firefox-esr.desktop
                    x-scheme-handler/mailto=amade-mail.desktop
                    inode/directory=amade-files.desktop
                    text/plain=amade-pad.desktop
                    application/vnd.oasis.opendocument.text=amade-writepad.desktop
                    image/jpeg=amade-imageviewer.desktop
                    image/png=amade-imageviewer.desktop
                    application/pdf=amade-pdfviewer.desktop
                    application/epub+zip=amade-books.desktop
                    audio/mpeg=amade-audio.desktop
                    video/mp4=amade-video.desktop
                    text/calendar=amade-calendar.desktop
                    text/vcard=amade-contacts.desktop
                    """);
            Set<String> ids = FakeApplications.ALL.stream().map(XdgDesktopEntry::id).collect(java.util.stream.Collectors.toSet());
            return new MimeApps(dir.resolve("user/mimeapps.list"), List.of(system), ids::contains);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static void waitFor(BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 100; i++) {
            AtomicReference<Boolean> met = new AtomicReference<>(false);
            SwingUtilities.invokeAndWait(() -> met.set(condition.getAsBoolean()));
            if (met.get()) return;
            Thread.sleep(50);
        }
        fail("condition not met in time");
    }

    private static javax.swing.AbstractButton findButton(java.awt.Container root, String text) {
        for (java.awt.Component c : root.getComponents()) {
            if (c instanceof javax.swing.AbstractButton b && text.equals(b.getText())) return b;
            if (c instanceof java.awt.Container inner) {
                javax.swing.AbstractButton found = findButton(inner, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    static final class FakeKeyring implements Keyring {
        volatile Status status = Status.NONE;
        volatile boolean locked;

        @Override
        public Status status() {
            return status;
        }

        @Override
        public void lock() {
            locked = true;
            status = new Status(status.available(), true, status.items());
        }

        @Override
        public void unlock(char[] password) throws IOException {
            if (!"secret".equals(new String(password))) throw new IOException("The password was invalid");
            locked = false;
            status = new Status(status.available(), false, status.items());
        }

        @Override
        public void changePassword(char[] current, char[] newPassword) {
        }
    }

    static final class FakeClock implements SystemClock {
        volatile Status status = new Status("Europe/Berlin", true, true, true);
        final List<String> calls = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public Status status() {
            return status;
        }

        @Override
        public void setZone(String zone) {
            calls.add("zone " + zone);
            status = new Status(zone, status.canNtp(), status.ntp(), status.synchronized_());
        }

        @Override
        public void setNtp(boolean on) {
            calls.add("ntp " + on);
            status = new Status(status.zone(), status.canNtp(), on, on);
        }

        @Override
        public void setTime(LocalDateTime time) throws IOException {
            if (status.ntp()) throw new IOException("Automatic time synchronization is enabled");
            calls.add("time " + time);
        }
    }
}
