package eu.amadeos.system.base.sound;

import java.util.List;
import java.util.Optional;

/**
 * A sound card (built-in audio, an HDMI connection, a USB or Bluetooth headset) with the ways it can be used,
 * e.g. "Analog Stereo Duplex" or "Digital Stereo (HDMI) Output". Choosing a profile decides which outputs and
 * inputs the card offers.
 *
 * @param name          the server's name, e.g. {@code alsa_card.pci-0000_00_1f.3}
 * @param description   what the user sees, e.g. "Built-in Audio"
 * @param profiles      the profiles in the server's order
 * @param activeProfile the name of the profile in use
 */
public record SoundCard(String name, String description, List<Profile> profiles, String activeProfile) {

    public SoundCard {
        profiles = List.copyOf(profiles);
        if (description == null || description.isBlank()) description = name;
    }

    /**
     * @param available false when nothing is plugged in for it, e.g. HDMI without a monitor with speakers
     */
    public record Profile(String name, String description, boolean available) {
    }

    public Optional<Profile> active() {
        return profiles.stream().filter(p -> p.name().equals(activeProfile)).findFirst();
    }
}
