package eu.amadeos.system.panel.cde.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.base.bluetooth.BluetoothDevice;
import eu.amadeos.system.base.bluetooth.BluetoothService;
import eu.amadeos.system.base.bluetooth.BluetoothStatus;
import eu.amadeos.system.base.bluetooth.PairingAgent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BluetoothCellTest {
    private static final Messages EN = Messages.load(BluetoothCell.class, NetworkCell.DESKTOP_TEXTS, Locale.ENGLISH);
    private static final Messages DE = Messages.load(BluetoothCell.class, NetworkCell.DESKTOP_TEXTS, Locale.GERMAN);
    private static final BluetoothDevice HEADPHONES = new BluetoothDevice("/org/bluez/hci0/dev_1", "38:18:4C:12:9A:E0",
            "WH-1000XM4", BluetoothDevice.Kind.HEADPHONES, true, true, 80, 0);
    private static final BluetoothDevice KEYBOARD = new BluetoothDevice("/org/bluez/hci0/dev_2", "C2:71:08:6B:1D:3E",
            "Keyboard K380", BluetoothDevice.Kind.KEYBOARD, true, false, -1, 0);

    static BluetoothStatus status(boolean powered, BluetoothDevice... devices) {
        return new BluetoothStatus(true, new BluetoothStatus.Adapter("/org/bluez/hci0", "F4:4E:FC:21:07:9A", "laptop",
                powered, false, false), List.of(devices));
    }

    @Test
    void shownOnlyWithAnAdapter() {
        AtomicInteger relayouts = new AtomicInteger();
        BluetoothCell cell = new BluetoothCell(new FakeService(), () -> { }, EN);
        cell.onAvailabilityChanged(relayouts::incrementAndGet);
        assertFalse(cell.available());
        cell.show(status(true));
        assertTrue(cell.available());
        assertEquals(1, relayouts.get());
        cell.show(new BluetoothStatus(true, null, List.of()));
        assertFalse(cell.available());
        assertEquals(2, relayouts.get());
    }

    @Test
    void tooltipNamesConnectedDevices() {
        BluetoothCell cell = new BluetoothCell(new FakeService(), () -> { }, EN);
        cell.show(status(false, HEADPHONES));
        assertEquals("Bluetooth is off", cell.component().getToolTipText());
        cell.show(status(true, KEYBOARD));
        assertEquals("Bluetooth is on, no device connected", cell.describe());
        cell.show(status(true, HEADPHONES, KEYBOARD));
        assertEquals("Bluetooth: WH-1000XM4 connected", cell.describe());
        assertEquals("WH-1000XM4 (80 %)", cell.label(HEADPHONES));
        BluetoothCell german = new BluetoothCell(new FakeService(), () -> { }, DE);
        german.show(status(true, HEADPHONES, new BluetoothDevice("/d3", "x", "MX Master 3",
                BluetoothDevice.Kind.MOUSE, true, true, 55, 0)));
        assertEquals("Bluetooth: 2 Geräte verbunden", german.describe());
    }

    @Test
    void menuListsPairedDevicesWithTicks() {
        BluetoothCell cell = new BluetoothCell(new FakeService(), () -> { }, EN);
        cell.show(status(true, HEADPHONES, KEYBOARD));
        JPopupMenu menu = cell.buildMenu();
        List<String> ticked = new ArrayList<>();
        List<String> items = new ArrayList<>();
        for (java.awt.Component c : menu.getComponents()) {
            if (c instanceof JCheckBoxMenuItem box) {
                items.add(box.getText());
                if (box.isSelected()) ticked.add(box.getText());
            } else if (c instanceof JMenuItem item) {
                items.add(item.getText());
            }
        }
        assertEquals(List.of("Bluetooth: WH-1000XM4 connected", "Bluetooth", "WH-1000XM4 (80 %)", "Keyboard K380",
                "Bluetooth settings…"), items);
        assertEquals(List.of("Bluetooth", "WH-1000XM4 (80 %)"), ticked);
    }

    @Test
    void aFifthCellOpensASecondSlot() {
        List<StatusCell> cells = new ArrayList<>();
        for (int i = 0; i < 5; i++) cells.add(new Plain());
        StatusDockWidget widget = new StatusDockWidget(cells);
        assertEquals(5, widget.shownCells());
        assertEquals(2, widget.shownSlots());
        assertEquals(2 * 58, widget.component().getPreferredSize().width);
        widget.setShown(cells.get(4), false);
        assertEquals(1, widget.shownSlots());
        assertEquals(58, widget.component().getPreferredSize().width);
    }

    private static final class Plain implements StatusCell {
        private final JLabel label = new JLabel();

        @Override
        public JComponent component() {
            return label;
        }

        @Override
        public void onThemeChanged(eu.amadeos.commons.theme.DesktopTheme theme) {
        }
    }

    private static final class FakeService implements BluetoothService {
        @Override
        public BluetoothStatus status() {
            return BluetoothStatus.UNAVAILABLE;
        }

        @Override
        public void setPowered(boolean on) {
        }

        @Override
        public void setDiscoverable(boolean on) {
        }

        @Override
        public void setSearching(boolean on) {
        }

        @Override
        public void pair(BluetoothDevice device, PairingAgent agent) {
        }

        @Override
        public void connect(BluetoothDevice device) {
        }

        @Override
        public void disconnect(BluetoothDevice device) {
        }

        @Override
        public void remove(BluetoothDevice device) {
        }

        @Override
        public AutoCloseable watch(Runnable onChange) {
            return () -> { };
        }
    }
}
