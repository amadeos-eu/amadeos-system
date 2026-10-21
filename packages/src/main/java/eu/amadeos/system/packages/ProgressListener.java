package eu.amadeos.system.packages;

/**
 * Hears from a running operation, on the thread that called the {@link PackageService}; a Swing caller
 * hands the calls on to the event dispatch thread itself.
 */
public interface ProgressListener {

    ProgressListener NONE = new ProgressListener() {
        @Override
        public void progress(Progress progress) {
        }

        @Override
        public void message(String line) {
        }
    };

    void progress(Progress progress);

    /** One line of apt's and dpkg's messages, for the log view. */
    void message(String line);
}
