package eu.amadeos.system.panel.cde.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.base.sound.AudioDevice;
import eu.amadeos.system.base.sound.SoundService;
import eu.amadeos.system.base.sound.SoundStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundCellTest {
    private static final Messages EN = Messages.load(SoundCell.class, NetworkCell.DESKTOP_TEXTS, Locale.ENGLISH);

    static final AudioDevice SPEAKERS = new AudioDevice("alsa_output.pci.analog-stereo",
            "Built-in Audio Analog Stereo", 45, false);
    static final AudioDevice HDMI = new AudioDevice("alsa_output.pci.hdmi-stereo",
            "HDMI / DisplayPort (DELL U2719D)", 100, false);
    static final AudioDevice HEADSET = new AudioDevice("alsa_output.usb-H390.analog-stereo",
            "H390 Headset Analog Stereo", 70, false);
    static final AudioDevice MIC = new AudioDevice("alsa_input.pci.analog-stereo",
            "Built-in Audio Analog Stereo", 100, false);
    static final AudioDevice HEADSET_MIC = new AudioDevice("alsa_input.usb-H390.mono",
            "H390 Headset Mono", 80, false);
    static final SoundStatus DESK = new SoundStatus(true, List.of(SPEAKERS, HDMI, HEADSET), SPEAKERS.name(),
            List.of(MIC, HEADSET_MIC), MIC.name());

    @Test
    void tooltipNamesVolumeAndOutput() {
        SoundCell cell = new SoundCell(new FakeService(), EN);
        assertEquals("The sound system is not running", cell.describe());
        cell.show(DESK);
        assertEquals("Volume 45 % · Built-in Audio Analog Stereo", cell.describe());
        assertTrue(cell.component().getToolTipText().contains("middle click: mute"));

        cell.show(new SoundStatus(true, List.of(new AudioDevice("x", "Speakers", 45, true)), "x", List.of(), ""));
        assertEquals("Muted · Speakers", cell.describe());
        assertTrue(cell.shownMuted());
    }

    @Test
    void menuOffersOutputsAndInputs() {
        SoundCell cell = new SoundCell(new FakeService(), EN);
        cell.show(DESK);
        JPopupMenu menu = cell.buildMenu(DESK);
        List<JMenuItem> items = items(menu);
        assertEquals(List.of("Output", "Built-in Audio Analog Stereo", "HDMI / DisplayPort (DELL U2719D)",
                        "H390 Headset Analog Stereo", "Input", "Built-in Audio Analog Stereo", "H390 Headset Mono",
                        "Mute microphone"),
                items.stream().map(JMenuItem::getText).toList());
        assertTrue(assertInstanceOf(JRadioButtonMenuItem.class, items.get(1)).isSelected());
        assertFalse(items.get(2).isSelected());
        assertInstanceOf(JCheckBoxMenuItem.class, items.get(7));
        assertEquals(45, cell.slider.getValue());
    }

    @Test
    void choosingAnOutputAndInputAsksTheServer() throws Exception {
        FakeService service = new FakeService();
        SoundCell cell = new SoundCell(service, EN);
        cell.show(DESK);
        List<JMenuItem> items = items(cell.buildMenu(DESK));
        items.get(3).doClick();
        assertEquals("output " + HEADSET.name(), service.calls.poll(5, TimeUnit.SECONDS));
        items.get(6).doClick();
        assertEquals("input " + HEADSET_MIC.name(), service.calls.poll(5, TimeUnit.SECONDS));
        cell.stop();
    }

    @Test
    void wheelChangesVolumeAndUnmutes() throws Exception {
        FakeService service = new FakeService();
        SoundCell cell = new SoundCell(service, EN);
        cell.show(new SoundStatus(true, List.of(new AudioDevice("x", "Speakers", 45, true)), "x", List.of(), ""));
        cell.changeVolume(SoundCell.WHEEL_STEP);
        assertEquals(50, cell.shownVolume());
        assertFalse(cell.shownMuted());
        assertEquals("mute false", service.calls.poll(5, TimeUnit.SECONDS));
        assertEquals("volume 50", service.calls.poll(5, TimeUnit.SECONDS));
        cell.stop();
    }

    @Test
    void withoutSoundServerTheMenuSaysSo() {
        SoundCell cell = new SoundCell(new FakeService(), EN);
        cell.changeVolume(10);
        assertEquals(0, cell.shownVolume());
        assertEquals(List.of("The sound system is not running"),
                items(cell.buildMenu(SoundStatus.UNAVAILABLE)).stream().map(JMenuItem::getText).toList());
    }

    @Test
    void wavesFollowTheVolume() {
        assertEquals(List.of(0, 1, 1, 2, 2, 3, 3),
                Arrays.stream(new int[]{0, 1, 33, 34, 66, 67, 120}).map(SoundIcons::waves).boxed().toList());
    }

    static List<JMenuItem> items(JComponent menu) {
        List<JMenuItem> items = new ArrayList<>();
        for (java.awt.Component c : menu.getComponents()) {
            if (c instanceof JMenuItem item) items.add(item);
        }
        return items;
    }

    static final class FakeService implements SoundService {
        final BlockingQueue<String> calls = new LinkedBlockingQueue<>();

        @Override
        public SoundStatus status() {
            return DESK;
        }

        @Override
        public void setVolume(int percent) {
            calls.add("volume " + percent);
        }

        @Override
        public void setMuted(boolean muted) {
            calls.add("mute " + muted);
        }

        @Override
        public void setOutput(AudioDevice device) {
            calls.add("output " + device.name());
        }

        @Override
        public void setInput(AudioDevice device) {
            calls.add("input " + device.name());
        }

        @Override
        public void setInputMuted(boolean muted) {
            calls.add("input mute " + muted);
        }

        @Override
        public AutoCloseable watch(Runnable onChange) {
            return () -> { };
        }
    }
}
