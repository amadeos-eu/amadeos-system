package org.datazuul.amadeos.wm.panel.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import org.datazuul.amadeos.commons.i18n.Messages;
import org.datazuul.amadeos.commons.theme.DesktopTheme;
import org.datazuul.amadeos.base.net.NetworkService;
import org.datazuul.amadeos.base.net.NetworkStatus;
import org.datazuul.amadeos.base.net.WifiNetwork;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class NetworkCellTest {
    private static final Messages EN = Messages.load(NetworkCell.class, NetworkCell.DESKTOP_TEXTS, Locale.ENGLISH);

    private static final NetworkStatus HOME = new NetworkStatus(NetworkStatus.State.CONNECTED,
            NetworkStatus.Kind.WIFI, "Home", 61, "wlp2s0", true);

    @Test
    void describesTheConnection() {
        NetworkCell cell = new NetworkCell(new FakeService(), () -> {}, EN);
        assertEquals("WLAN Home, signal 61 %", cell.describe(HOME));
        assertEquals("Wired network: Wired connection 1", cell.describe(new NetworkStatus(
                NetworkStatus.State.CONNECTED, NetworkStatus.Kind.ETHERNET, "Wired connection 1", 0, null, false)));
        assertEquals("Network service is not running", cell.describe(NetworkStatus.UNAVAILABLE));

        cell.show(HOME);
        assertEquals("WLAN Home, signal 61 %", cell.component().getToolTipText());
        assertEquals(HOME, cell.status());
    }

    @Test
    void menuListsNetworksWithTheActiveOneAsSubmenu() {
        NetworkCell cell = new NetworkCell(new FakeService(), () -> {}, EN);
        JPopupMenu menu = cell.buildMenu(HOME, List.of(
                new WifiNetwork("Home", 61, true, true, true),
                new WifiNetwork("Cafe", 40, false, false, false)));

        List<String> texts = items(menu).stream().map(JMenuItem::getText).toList();
        assertEquals(List.of("WLAN Home, signal 61 %", "WLAN", "Home", "Cafe", "Search again", "Network settings…"),
                texts);
        assertInstanceOf(JCheckBoxMenuItem.class, items(menu).get(1));
        JMenu active = assertInstanceOf(JMenu.class, items(menu).get(2));
        assertEquals("Disconnect", active.getItem(0).getText());
        assertEquals("Forget network", active.getItem(1).getText());
    }

    @Test
    void withoutWlanOnlyStatusAndSettings() {
        List<Boolean> opened = new ArrayList<>();
        NetworkCell cell = new NetworkCell(new FakeService(), () -> opened.add(true), EN);
        NetworkStatus wired = new NetworkStatus(NetworkStatus.State.CONNECTED, NetworkStatus.Kind.ETHERNET,
                "Wired connection 1", 0, null, false);
        JPopupMenu menu = cell.buildMenu(wired, List.of());
        List<JMenuItem> items = items(menu);
        assertEquals(List.of("Wired network: Wired connection 1", "Network settings…"),
                items.stream().map(JMenuItem::getText).toList());
        items.get(1).doClick();
        assertEquals(List.of(true), opened);
    }

    @Test
    void joiningAnOpenNetworkNeedsNoPassword() throws Exception {
        FakeService service = new FakeService();
        NetworkCell cell = new NetworkCell(service, () -> {}, EN);
        WifiNetwork library = new WifiNetwork("Library", 50, false, false, false);
        cell.join(library);
        assertEquals(NetworkStatus.State.CONNECTING, cell.status().state());
        assertEquals(library, service.connected.take());
        cell.stop();
    }

    @Test
    void slotShrinksToTheAvailableCells() {
        StatusDockWidget one = new StatusDockWidget(List.of(new NetworkCell(new FakeService(), () -> {}, EN)));
        assertEquals(1, one.shownCells());

        StatusDockWidget some = new StatusDockWidget(List.of(new Blank(true), new Blank(false), new Blank(true)));
        assertEquals(2, some.shownCells());
    }

    private static List<JMenuItem> items(JPopupMenu menu) {
        return Arrays.stream(menu.getComponents()).filter(JMenuItem.class::isInstance).map(JMenuItem.class::cast)
                .toList();
    }

    private record Blank(boolean available) implements StatusCell {
        @Override
        public JComponent component() {
            return new JPanel();
        }

        @Override
        public void onThemeChanged(DesktopTheme theme) {
        }
    }

    private static final class FakeService implements NetworkService {
        final java.util.concurrent.BlockingQueue<WifiNetwork> connected = new java.util.concurrent.LinkedBlockingQueue<>();

        @Override
        public NetworkStatus status() {
            return HOME;
        }

        @Override
        public List<WifiNetwork> wifiNetworks(boolean rescan) {
            return List.of();
        }

        @Override
        public void connect(WifiNetwork network, String password) {
            connected.add(network);
        }

        @Override
        public void disconnectWifi() {
        }

        @Override
        public void forget(WifiNetwork network) {
        }

        @Override
        public void setWifiEnabled(boolean enabled) {
        }

        @Override
        public AutoCloseable watch(Runnable onChange) {
            return () -> {};
        }
    }
}
