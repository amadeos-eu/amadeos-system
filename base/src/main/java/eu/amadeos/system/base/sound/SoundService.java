package eu.amadeos.system.base.sound;

/**
 * The system volume and the choice of output and input device. Calls start a process and block for a
 * moment, so callers run them off the event dispatch thread.
 */
public interface SoundService {

    SoundStatus status();

    /** Sets the volume of the output in use, 0-150 percent. */
    void setVolume(int percent) throws SoundException;

    void setMuted(boolean muted) throws SoundException;

    /** Makes {@code device} the output in use and moves the sounds that are playing over to it. */
    void setOutput(AudioDevice device) throws SoundException;

    /** Makes {@code device} the input in use and moves running recordings over to it. */
    void setInput(AudioDevice device) throws SoundException;

    void setInputMuted(boolean muted) throws SoundException;

    /** Sets the volume of one output, in use or not, 0-150 percent. */
    default void setOutputVolume(AudioDevice output, int percent) throws SoundException {
        throw new SoundException("not supported");
    }

    default void setOutputMuted(AudioDevice output, boolean muted) throws SoundException {
        throw new SoundException("not supported");
    }

    /** Sets the recording level of one input, 0-150 percent. */
    default void setInputVolume(AudioDevice input, int percent) throws SoundException {
        throw new SoundException("not supported");
    }

    default void setInputMuted(AudioDevice input, boolean muted) throws SoundException {
        throw new SoundException("not supported");
    }

    /** The sound cards with their profiles. */
    default java.util.List<SoundCard> cards() {
        return java.util.List.of();
    }

    /** Switches a card to another profile, e.g. from analog output to HDMI. */
    default void setProfile(SoundCard card, String profile) throws SoundException {
        throw new SoundException("not supported");
    }

    /**
     * Calls {@code onChange} (on a background thread) whenever a device, its volume or the choice changes,
     * also when another program changed it, until the returned handle is closed.
     */
    AutoCloseable watch(Runnable onChange);

    /** A request the sound server refused, with its reason. */
    final class SoundException extends Exception {
        public SoundException(String message) {
            super(message);
        }
    }
}
