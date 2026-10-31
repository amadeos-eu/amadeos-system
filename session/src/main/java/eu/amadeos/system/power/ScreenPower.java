package eu.amadeos.system.power;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * Turns the monitors off through the X server's DPMS: after a number of seconds without input (the X server
 * counts, and any input turns them on again) or at once, as the lid action "Turn screen off" does.
 */
public interface ScreenPower {

    /** Seconds without input before the X server turns the monitors off; 0 for never. */
    void setTimeout(int seconds);

    void turnOff();

    /** Through {@code xset} (package x11-xserver-utils). */
    static ScreenPower xset() {
        return new ScreenPower() {
            private static final Logger LOGGER = Logger.getLogger(ScreenPower.class.getName());

            @Override
            public void setTimeout(int seconds) {
                // Standby and suspend are left out: modern monitors know only on and off.
                run(seconds <= 0 ? List.of("xset", "-dpms")
                        : List.of("xset", "+dpms", "dpms", "0", "0", Integer.toString(seconds)));
                // The X server's own blanking would go dark before the amadeOS screen saver: off.
                run(List.of("xset", "s", "off"));
            }

            @Override
            public void turnOff() {
                run(List.of("xset", "+dpms"));
                run(List.of("xset", "dpms", "force", "off"));
            }

            private void run(List<String> command) {
                try {
                    Process p = new ProcessBuilder(command).redirectErrorStream(true)
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                    p.waitFor();
                } catch (IOException e) {
                    LOGGER.warning(() -> "Cannot run " + command + ": " + e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
    }
}
