package eu.amadeos.system.base.sound;

/**
 * A sound output (a PulseAudio sink: speakers, headphones, HDMI) or input (a source: microphone, line in).
 *
 * @param name        the server's name for it, e.g. {@code alsa_output.pci-0000_00_1f.3.analog-stereo}
 * @param description what the user sees, e.g. "Built-in Audio Analog Stereo"
 * @param volume      volume in percent, averaged over the channels; above 100 when boosted
 * @param muted       whether it is muted
 */
public record AudioDevice(String name, String description, int volume, boolean muted) {

    public AudioDevice {
        if (description == null || description.isBlank()) description = name;
    }
}
