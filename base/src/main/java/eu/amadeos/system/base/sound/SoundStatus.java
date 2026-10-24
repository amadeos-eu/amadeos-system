package eu.amadeos.system.base.sound;

import java.util.List;
import java.util.Optional;

/**
 * What the front panel shows about sound: the outputs and inputs and which of them programs use.
 *
 * @param available     whether the sound server answered
 * @param outputs       outputs in the server's order
 * @param defaultOutput name of the output new and following sounds go to, empty when there is none
 * @param inputs        inputs, without the monitors of the outputs
 * @param defaultInput  name of the input programs record from, empty when there is none
 */
public record SoundStatus(boolean available, List<AudioDevice> outputs, String defaultOutput,
                          List<AudioDevice> inputs, String defaultInput) {

    /** The sound server is not installed or not running. */
    public static final SoundStatus UNAVAILABLE = new SoundStatus(false, List.of(), "", List.of(), "");

    public SoundStatus {
        outputs = List.copyOf(outputs);
        inputs = List.copyOf(inputs);
        if (defaultOutput == null) defaultOutput = "";
        if (defaultInput == null) defaultInput = "";
    }

    /** The output in use; the first one when the server names none that exists. */
    public Optional<AudioDevice> output() {
        return find(outputs, defaultOutput);
    }

    /** The input in use; the first one when the server names none that exists. */
    public Optional<AudioDevice> input() {
        return find(inputs, defaultInput);
    }

    /** Volume of the output in use, 0 without one. */
    public int volume() {
        return output().map(AudioDevice::volume).orElse(0);
    }

    /** Whether the output in use is muted; true without one, since nothing can be heard. */
    public boolean muted() {
        return output().map(AudioDevice::muted).orElse(true);
    }

    private static Optional<AudioDevice> find(List<AudioDevice> devices, String name) {
        return devices.stream().filter(d -> d.name().equals(name)).findFirst()
                .or(() -> devices.stream().findFirst());
    }
}
