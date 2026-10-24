package eu.amadeos.system.base.sound;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PactlTest {

    private static final String SINKS = """
            Sink #48
            \tState: SUSPENDED
            \tName: alsa_output.pci-0000_00_1f.3.analog-stereo
            \tDescription: Built-in Audio Analog Stereo
            \tDriver: PipeWire
            \tMute: no
            \tVolume: front-left: 29491 /  45% / -20.81 dB,   front-right: 29491 /  45% / -20.81 dB
            \t        balance 0.00
            \tBase Volume: 65536 / 100% / 0.00 dB
            \tMonitor Source: alsa_output.pci-0000_00_1f.3.analog-stereo.monitor
            \tProperties:
            \t\tdevice.description = "Built-in Audio Analog Stereo"
            \t\tdevice.volume = "100%"
            \tPorts:
            \t\tanalog-output-speaker: Speakers (type: Speaker, priority: 10000, availability unknown)
            \tActive Port: analog-output-speaker

            Sink #61
            \tState: RUNNING
            \tName: alsa_output.usb-Logitech_H390-00.analog-stereo
            \tDescription: H390 Headset Analog Stereo
            \tMute: yes
            \tVolume: front-left: 52428 /  80% / -5.81 dB,   front-right: 45875 /  70% / -9.29 dB
            """;

    private static final String SOURCES = """
            Source #49
            \tName: alsa_output.pci-0000_00_1f.3.analog-stereo.monitor
            \tDescription: Monitor of Built-in Audio Analog Stereo
            \tMute: no
            \tVolume: front-left: 65536 / 100% / 0.00 dB,   front-right: 65536 / 100% / 0.00 dB
            \tMonitor of Sink: alsa_output.pci-0000_00_1f.3.analog-stereo
            Source #50
            \tName: alsa_input.pci-0000_00_1f.3.analog-stereo
            \tDescription: Built-in Audio Analog Stereo
            \tMute: no
            \tVolume: front-left: 65536 / 100% / 0.00 dB,   front-right: 65536 / 100% / 0.00 dB
            \tMonitor of Sink: n/a
            """;

    @Test
    void readsOutputsWithVolumeAndMute() {
        List<AudioDevice> sinks = Pactl.parseDevices(SINKS);
        assertEquals(2, sinks.size());
        assertEquals(new AudioDevice("alsa_output.pci-0000_00_1f.3.analog-stereo",
                "Built-in Audio Analog Stereo", 45, false), sinks.get(0));
        assertEquals("H390 Headset Analog Stereo", sinks.get(1).description());
        assertEquals(75, sinks.get(1).volume());
        assertTrue(sinks.get(1).muted());
    }

    @Test
    void leavesOutTheMonitorsOfOutputs() {
        List<AudioDevice> sources = Pactl.parseDevices(SOURCES);
        assertEquals(List.of("alsa_input.pci-0000_00_1f.3.analog-stereo"),
                sources.stream().map(AudioDevice::name).toList());
    }

    @Test
    void readsTheDefaultDevices() {
        String info = """
                Server String: /run/user/1000/pulse/native
                Server Name: PulseAudio (on PipeWire 1.4.2)
                Default Sink: alsa_output.usb-Logitech_H390-00.analog-stereo
                Default Source: alsa_input.pci-0000_00_1f.3.analog-stereo
                """;
        assertEquals("alsa_output.usb-Logitech_H390-00.analog-stereo", Pactl.parseDefault(info, "Default Sink"));
        assertEquals("alsa_input.pci-0000_00_1f.3.analog-stereo", Pactl.parseDefault(info, "Default Source"));
        assertEquals("", Pactl.parseDefault("", "Default Sink"));
    }

    @Test
    void statusFallsBackToTheFirstOutput() {
        SoundStatus s = new SoundStatus(true, Pactl.parseDevices(SINKS), "gone", List.of(), "");
        assertEquals(45, s.volume());
        assertFalse(s.muted());
        assertTrue(SoundStatus.UNAVAILABLE.muted());
        assertEquals(0, SoundStatus.UNAVAILABLE.volume());
    }

    @Test
    void volumeIsKeptInRange() {
        assertEquals("150%", Pactl.setVolume(400).get(3));
        assertEquals("0%", Pactl.setVolume(-5).get(3));
    }

    @Test
    void reactsOnlyToDeviceAndServerEvents() {
        assertTrue(Pactl.relevantEvent("Event 'change' on sink #48"));
        assertTrue(Pactl.relevantEvent("Event 'new' on source #77"));
        assertTrue(Pactl.relevantEvent("Event 'change' on server #4294967295"));
        assertFalse(Pactl.relevantEvent("Event 'change' on sink-input #120"));
        assertFalse(Pactl.relevantEvent("Event 'new' on client #300"));
    }

    @Test
    void readsStreamIdsAndErrors() {
        assertEquals(List.of("120", "131"), Pactl.parseIds("120\t48\t118\tPipeWire\tfloat32le 2ch 48000Hz\n131\t48\t\n"));
        assertEquals("No such entity", Pactl.errorMessage("Failure: No such entity\n"));
    }
}
