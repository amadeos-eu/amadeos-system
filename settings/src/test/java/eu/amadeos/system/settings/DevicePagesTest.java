package eu.amadeos.system.settings;

import eu.amadeos.system.base.net.WifiNetwork;
import eu.amadeos.system.base.bluetooth.BluetoothDevice;
import eu.amadeos.system.print.cups.Printer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The Network connections, Sound, Bluetooth and Printers pages with the stand-in devices of {@link FakeDevices}. */
class DevicePagesTest {

    @BeforeAll
    static void english() {
        SettingsTexts.use(Locale.ENGLISH);
    }

    @Test
    void devicePagesSitInTheirGroupsAndActAtOnce() {
        assertEquals(SettingsCategory.Group.INPUT, SettingsCategory.SOUND.group());
        assertEquals(SettingsCategory.Group.DEVICES, SettingsCategory.NETWORK.group());
        assertEquals(SettingsCategory.Group.DEVICES, SettingsCategory.PRINTERS.group());
        assertTrue(SettingsCategory.BLUETOOTH.immediate());
        assertFalse(SettingsCategory.NETWORK.immediate(), "the proxy tab needs Apply");
        assertTrue(SettingsCategory.PRINTERS.matches("duplex"));
        assertTrue(SettingsCategory.NETWORK.matches("wifi"));
        assertTrue(SettingsCategory.SOUND.matches("headphones"));
    }

    @Test
    void textsOfDevicesAndPrinters() {
        assertEquals("Home – connected", ConnectionsPanel.describe(new WifiNetwork("Home", 80, true, true, true)));
        assertEquals("Library", ConnectionsPanel.describe(new WifiNetwork("Library", 30, false, false, false)));
        assertEquals("WH-1000XM4 – connected, battery 80 %", BluetoothPage.describe(new BluetoothDevice("/d", "38:18",
                "WH-1000XM4", BluetoothDevice.Kind.HEADPHONES, true, true, 80, 0), true));
        assertEquals("123 456", BluetoothPage.group("123456"));
        assertEquals("ipp://192.168.1.40/ipp/print", PrintersPage.uri(" 192.168.1.40 "));
        assertEquals("ipps://printer.local/ipp/print", PrintersPage.uri("ipps://printer.local/ipp/print"));
        assertEquals("Long edge (portrait)", PrintersPage.duplexLabel("DuplexNoTumble"));
        Printer p = new Printer("HP", "HP OfficeJet", "Hall", "HP OfficeJet Pro", Printer.State.PROCESSING, "", List.of(),
                true, "ipp://localhost/printers/HP", 2, true);
        assertEquals("<html><b>HP OfficeJet (default)</b><br>Printing · HP OfficeJet Pro · Hall · 2 jobs waiting</html>",
                PrintersPage.describe(p));
    }

    @Test
    void testToneIsAStereoWaveOnOneSide() {
        byte[] wav = PulseSoundCheck.tone(SoundCheck.Channel.LEFT);
        assertEquals("RIFF", new String(wav, 0, 4));
        int frames = (wav.length - 44) / 4;
        assertEquals(PulseSoundCheck.RATE * 7 / 10, frames);
        boolean left = false;
        for (int i = 44; i < wav.length; i += 4) {
            if (wav[i] != 0 || wav[i + 1] != 0) left = true;
            assertEquals(0, wav[i + 2] | wav[i + 3], "the right channel stays silent");
        }
        assertTrue(left);
        assertEquals(0, PulseSoundCheck.peak(new byte[8], 8));
        assertEquals(100, PulseSoundCheck.peak(new byte[]{(byte) 0xff, 0x7f}, 2));
    }

    @Test
    void soundPageChoosesTheOutputAndTestsIt() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        FakeDevices.Sound sound = new FakeDevices.Sound();
        FakeDevices.Check check = new FakeDevices.Check();
        AtomicReference<SoundPage> page = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> page.set(new SoundPage(s -> { }, sound, check)));
        waitFor(() -> page.get().outputList().getModel().getSize() == 3);
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(0, page.get().outputList().getSelectedIndex());
            assertEquals(65, page.get().outputVolume().getValue());
            page.get().outputList().setSelectedIndex(1);
            button(page.get(), "Right").doClick();
        });
        waitFor(() -> sound.calls.contains("output alsa_output.pci-0000_00_1f.3.hdmi-stereo"));
        assertEquals(List.of("test alsa_output.pci-0000_00_1f.3.analog-stereo RIGHT"), check.calls);
    }

    @Test
    void bluetoothPageListsDevicesAndPairs() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        FakeDevices.Bluetooth bt = new FakeDevices.Bluetooth();
        AtomicReference<BluetoothPage> page = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> page.set(new BluetoothPage(s -> { }, bt)));
        waitFor(() -> page.get().pairedList().getModel().getSize() == 3);
        SwingUtilities.invokeAndWait(() -> {
            assertEquals("MX Master 3", page.get().pairedList().getModel().getElementAt(0).name(),
                    "connected devices first, by name");
            assertTrue(page.get().powered().isSelected());
            page.get().pairedList().setSelectedIndex(2);
            button(page.get(), "Connect").doClick();
        });
        waitFor(() -> bt.calls.contains("connect Keyboard K380"));
        SwingUtilities.invokeAndWait(() -> page.get().powered().doClick());
        waitFor(() -> bt.calls.contains("powered false"));
        waitFor(() -> page.get().pairedList().getModel().getSize() == 0);
    }

    @Test
    void printersPageMakesDefaultAndPrintsATestPage() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        FakeDevices.Printers printers = new FakeDevices.Printers();
        AtomicReference<PrintersPage> page = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> page.set(new PrintersPage(s -> { }, printers, c -> { })));
        waitFor(() -> page.get().printerList().getModel().getSize() == 2);
        SwingUtilities.invokeAndWait(() -> {
            page.get().printerList().setSelectedIndex(1);
            button(page.get(), "Make Default").doClick();
        });
        waitFor(() -> printers.calls.contains("default HP_OfficeJet_Pro_9010"));
        // Buttons wait while a change runs.
        waitFor(() -> button(page.get(), "Print Test Page").isEnabled());
        SwingUtilities.invokeAndWait(() -> button(page.get(), "Print Test Page").doClick());
        waitFor(() -> printers.calls.contains("test page HP_OfficeJet_Pro_9010"));
    }

    @Test
    void connectionsConnectToASavedNetworkWithoutAskingAPassword() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        FakeDevices.Network network = new FakeDevices.Network();
        AtomicReference<ConnectionsPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(new ConnectionsPanel(network, s -> { })));
        waitFor(() -> panel.get().networkList().getModel().getSize() == 5);
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(2, panel.get().devices().size());
            panel.get().networkList().setSelectedIndex(1);
            button(panel.get(), "Connect").doClick();
        });
        waitFor(() -> network.calls.contains("connect Cafe Central "));
    }

    static AbstractButton button(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof AbstractButton b && text.equals(b.getText()) && b.isShowing() == root.isShowing()) return b;
            if (c instanceof Container inner) {
                AbstractButton found = button(inner, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    static void waitFor(BooleanSupplier condition) throws Exception {
        long end = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < end) {
            AtomicReference<Boolean> ok = new AtomicReference<>(false);
            SwingUtilities.invokeAndWait(() -> ok.set(condition.getAsBoolean()));
            if (ok.get()) return;
            Thread.sleep(50);
        }
        throw new AssertionError("condition not met in time");
    }
}
