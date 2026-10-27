package eu.amadeos.system.settings;

import eu.amadeos.system.base.sound.AudioDevice;

import java.util.function.IntConsumer;

/** Hearing and seeing whether a sound device works: a test tone on an output, the level of an input. */
interface SoundCheck {

    enum Channel { LEFT, RIGHT, BOTH }

    /** Plays a short tone on one output, on one side or both; returns at once. */
    void playTest(AudioDevice output, Channel channel);

    /**
     * Reports the level of an input, 0-100, about ten times a second (on a background thread) until the returned
     * handle is closed.
     */
    AutoCloseable meter(AudioDevice input, IntConsumer level);
}
