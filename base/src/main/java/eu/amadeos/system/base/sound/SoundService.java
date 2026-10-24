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
