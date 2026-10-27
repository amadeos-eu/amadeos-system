package eu.amadeos.system.settings;

import eu.amadeos.system.bluetooth.BluetoothDevice;
import eu.amadeos.system.bluetooth.BluetoothService;
import eu.amadeos.system.bluetooth.BluetoothStatus;
import eu.amadeos.system.bluetooth.PairingAgent;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Dimension;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Bluetooth: switching it on and off, the name and visibility of this computer, the paired devices (connect,
 * disconnect, remove) and searching for and pairing new ones, through BlueZ ({@link BluetoothService}). Pairing
 * asks the user here when the device shows a number or wants a PIN. Everything acts at once.
 */
final class BluetoothPage extends SettingsPage {
    private final BluetoothService bluetooth;

    private final JCheckBox powered = new JCheckBox(SettingsTexts.get("bluetooth.on"));
    private final JCheckBox visible = new JCheckBox(SettingsTexts.get("bluetooth.visible"));
    private final JLabel name = new JLabel(" ");
    private final JLabel state = new JLabel(" ");
    private final DefaultListModel<BluetoothDevice> paired = new DefaultListModel<>();
    private final JList<BluetoothDevice> pairedList = new JList<>(paired);
    private final JButton connect = new JButton(SettingsTexts.get("bluetooth.connect"));
    private final JButton disconnect = new JButton(SettingsTexts.get("bluetooth.disconnect"));
    private final JButton remove = new JButton(SettingsTexts.get("bluetooth.remove"));
    private final DefaultListModel<BluetoothDevice> nearby = new DefaultListModel<>();
    private final JList<BluetoothDevice> nearbyList = new JList<>(nearby);
    private final JButton search = new JButton(SettingsTexts.get("bluetooth.search"));
    private final JButton pair = new JButton(SettingsTexts.get("bluetooth.pair"));
    private final JLabel searching = new JLabel(" ");
    private final List<JPanel> deviceSections = new ArrayList<>();

    private BluetoothStatus current = BluetoothStatus.UNAVAILABLE;
    private boolean updating;
    private boolean busy;
    private AutoCloseable watch;
    private final Timer refreshLater = new Timer(300, e -> refresh());

    BluetoothPage(Consumer<String> status, BluetoothService bluetooth) {
        super(SettingsCategory.BLUETOOTH, status);
        this.bluetooth = bluetooth;
        refreshLater.setRepeats(false);

        powered.addActionListener(e -> {
            if (!updating) act(() -> bluetooth.setPowered(powered.isSelected()),
                    SettingsTexts.get(powered.isSelected() ? "bluetooth.status.on" : "bluetooth.status.off"));
        });
        visible.addActionListener(e -> {
            if (!updating) act(() -> bluetooth.setDiscoverable(visible.isSelected()), null);
        });
        setUpList(pairedList, true);
        setUpList(nearbyList, false);
        pairedList.addListSelectionListener(e -> enableButtons());
        nearbyList.addListSelectionListener(e -> enableButtons());
        connect.addActionListener(e -> withSelected(pairedList, d -> act(() -> bluetooth.connect(d),
                SettingsTexts.format("bluetooth.status.connected", d.name()))));
        disconnect.addActionListener(e -> withSelected(pairedList, d -> act(() -> bluetooth.disconnect(d),
                SettingsTexts.format("bluetooth.status.disconnected", d.name()))));
        remove.addActionListener(e -> withSelected(pairedList, this::confirmRemove));
        search.addActionListener(e -> {
            boolean on = current.hasAdapter() && !current.adapter().discovering();
            act(() -> bluetooth.setSearching(on), null);
        });
        pair.addActionListener(e -> withSelected(nearbyList, this::pairWith));

        JPanel pairedSection = section(SettingsTexts.get("bluetooth.paired.title"), line(scroll(pairedList)),
                line(connect, disconnect, remove));
        JPanel nearbySection = section(SettingsTexts.get("bluetooth.nearby.title"), line(search, searching),
                line(scroll(nearbyList)), line(pair), note(SettingsTexts.get("bluetooth.nearby.hint")));
        deviceSections.add(pairedSection);
        deviceSections.add(nearbySection);
        layoutSections(
                section(SettingsTexts.get("bluetooth.adapter.title"), powered, line(name), visible, line(state)),
                pairedSection, nearbySection,
                note(SettingsTexts.get("bluetooth.hint")));
        show(BluetoothStatus.UNAVAILABLE);
        refresh();
    }

    private static void setUpList(JList<BluetoothDevice> list, boolean pairedDevices) {
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(4);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected,
                                                          boolean focus) {
                BluetoothDevice d = (BluetoothDevice) value;
                super.getListCellRendererComponent(l, describe(d, pairedDevices), index, selected, focus);
                setIcon(DeviceIcons.bluetooth(d.kind()));
                setIconTextGap(8);
                return this;
            }
        });
    }

    /** "Headphones WH-1000 - connected, battery 80 %" or, while searching, "Pixel 7". */
    static String describe(BluetoothDevice d, boolean pairedDevice) {
        if (!pairedDevice) return d.name();
        String state = SettingsTexts.get(d.connected() ? "bluetooth.device.connected" : "bluetooth.device.not-connected");
        if (d.connected() && d.battery() >= 0) state += ", " + SettingsTexts.format("bluetooth.device.battery", d.battery());
        return SettingsTexts.format("bluetooth.device", d.name(), state);
    }

    private static JScrollPane scroll(JList<?> list) {
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(420, 92));
        return scroll;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (watch == null) watch = bluetooth.watch(() -> SwingUtilities.invokeLater(refreshLater::restart));
    }

    @Override
    public void removeNotify() {
        if (watch != null) {
            try {
                watch.close();
            } catch (Exception ignored) {
                // nothing left to do
            }
            watch = null;
        }
        // A search costs battery and slows down other connections; it ends with the page.
        if (current.hasAdapter() && current.adapter().discovering()) {
            Thread.ofVirtual().start(() -> {
                try {
                    bluetooth.setSearching(false);
                } catch (BluetoothService.BluetoothException ignored) {
                    // the adapter is gone
                }
            });
        }
        super.removeNotify();
    }

    void refresh() {
        new SwingWorker<BluetoothStatus, Void>() {
            @Override
            protected BluetoothStatus doInBackground() {
                return bluetooth.status();
            }

            @Override
            protected void done() {
                try {
                    show(get());
                } catch (Exception e) {
                    show(BluetoothStatus.UNAVAILABLE);
                }
            }
        }.execute();
    }

    void show(BluetoothStatus status) {
        current = status;
        updating = true;
        try {
            BluetoothStatus.Adapter a = status.adapter();
            boolean on = a != null && a.powered();
            powered.setSelected(on);
            powered.setEnabled(a != null);
            visible.setSelected(a != null && a.discoverable());
            visible.setEnabled(on);
            name.setText(a == null ? " " : SettingsTexts.format("bluetooth.name", a.name()));
            state.setText(!status.available() ? SettingsTexts.get("bluetooth.unavailable")
                    : a == null ? SettingsTexts.get("bluetooth.no-adapter") : " ");
            state.setVisible(!status.available() || a == null);
            keepSelection(pairedList, paired, on ? status.paired() : List.of());
            keepSelection(nearbyList, nearby, on ? status.nearby() : List.of());
            boolean discovering = a != null && a.discovering();
            search.setText(SettingsTexts.get(discovering ? "bluetooth.search.stop" : "bluetooth.search"));
            searching.setText(discovering ? SettingsTexts.get("bluetooth.searching") : " ");
            for (JPanel section : deviceSections) section.setVisible(on);
        } finally {
            updating = false;
        }
        enableButtons();
    }

    private static void keepSelection(JList<BluetoothDevice> list, DefaultListModel<BluetoothDevice> model,
                                      List<BluetoothDevice> devices) {
        String selected = list.getSelectedValue() == null ? null : list.getSelectedValue().id();
        model.clear();
        devices.forEach(model::addElement);
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).id().equals(selected)) list.setSelectedIndex(i);
        }
    }

    private void enableButtons() {
        BluetoothDevice p = pairedList.getSelectedValue();
        connect.setEnabled(!busy && p != null && !p.connected());
        disconnect.setEnabled(!busy && p != null && p.connected());
        remove.setEnabled(!busy && p != null);
        pair.setEnabled(!busy && nearbyList.getSelectedValue() != null);
        search.setEnabled(!busy && current.hasAdapter() && current.adapter().powered());
    }

    private static void withSelected(JList<BluetoothDevice> list, Consumer<BluetoothDevice> action) {
        BluetoothDevice d = list.getSelectedValue();
        if (d != null) action.accept(d);
    }

    private void confirmRemove(BluetoothDevice d) {
        int answer = JOptionPane.showConfirmDialog(this, SettingsTexts.format("bluetooth.remove.question", d.name()),
                SettingsTexts.get("bluetooth.remove.title"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer == JOptionPane.OK_OPTION) {
            act(() -> bluetooth.remove(d), SettingsTexts.format("bluetooth.status.removed", d.name()));
        }
    }

    private void pairWith(BluetoothDevice d) {
        status(SettingsTexts.format("bluetooth.status.pairing", d.name()));
        act(() -> bluetooth.pair(d, new DialogAgent()), SettingsTexts.format("bluetooth.status.paired", d.name()));
    }

    private interface BluetoothCall {
        void run() throws BluetoothService.BluetoothException;
    }

    /** Runs a call in the background; the page shows its outcome on the status line and reads the devices again. */
    private void act(BluetoothCall call, String success) {
        busy = true;
        enableButtons();
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                try {
                    call.run();
                    return null;
                } catch (BluetoothService.BluetoothException e) {
                    return e.getMessage();
                }
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    String error = get();
                    if (error != null) status(SettingsTexts.format("status.error", error));
                    else if (success != null) status(success);
                } catch (Exception e) {
                    status(SettingsTexts.format("status.error", e.getMessage()));
                }
                refresh();
            }
        }.execute();
    }

    /** Asks the user on the event thread; bluetoothd waits meanwhile. */
    private final class DialogAgent implements PairingAgent {
        private final AtomicReference<JDialog> shown = new AtomicReference<>();

        @Override
        public boolean confirm(BluetoothDevice device, String passkey) {
            return onEdt(() -> JOptionPane.showConfirmDialog(BluetoothPage.this,
                    SettingsTexts.format("bluetooth.confirm", device.name(), group(passkey)),
                    SettingsTexts.get("bluetooth.pair.title"), JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION);
        }

        @Override
        public void showPasskey(BluetoothDevice device, String passkey) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane pane = new JOptionPane(SettingsTexts.format("bluetooth.type", device.name(), passkey),
                        JOptionPane.INFORMATION_MESSAGE);
                JDialog dialog = pane.createDialog(BluetoothPage.this, SettingsTexts.get("bluetooth.pair.title"));
                dialog.setModal(false);
                close();
                shown.set(dialog);
                dialog.setVisible(true);
            });
        }

        @Override
        public String requestPin(BluetoothDevice device) {
            return onEdt(() -> JOptionPane.showInputDialog(BluetoothPage.this,
                    SettingsTexts.format("bluetooth.pin", device.name()), SettingsTexts.get("bluetooth.pair.title"),
                    JOptionPane.QUESTION_MESSAGE));
        }

        @Override
        public void done() {
            SwingUtilities.invokeLater(this::close);
        }

        private void close() {
            JDialog dialog = shown.getAndSet(null);
            if (dialog != null) dialog.dispose();
        }

        private static <T> T onEdt(java.util.function.Supplier<T> question) {
            AtomicReference<T> answer = new AtomicReference<>();
            try {
                SwingUtilities.invokeAndWait(() -> answer.set(question.get()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (InvocationTargetException e) {
                throw new IllegalStateException(e.getCause());
            }
            return answer.get();
        }
    }

    /** "123456" as "123 456", easier to compare. */
    static String group(String passkey) {
        return passkey.length() == 6 ? passkey.substring(0, 3) + " " + passkey.substring(3) : passkey;
    }

    JList<BluetoothDevice> pairedList() {
        return pairedList;
    }

    JCheckBox powered() {
        return powered;
    }

    @Override
    protected String apply() {
        return null;
    }
}
