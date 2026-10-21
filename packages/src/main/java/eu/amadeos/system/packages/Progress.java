package eu.amadeos.system.packages;

/**
 * A step of a running apt operation, from apt's status lines ({@code APT::Status-Fd}).
 *
 * @param phase   downloading or changing the installed packages
 * @param percent 0-100 within the phase
 * @param item    the package being worked on, or apt's item number while downloading
 * @param message apt's description of the step ("Unpacking xz-utils (amd64)")
 */
public record Progress(Phase phase, int percent, String item, String message) {

    public enum Phase { DOWNLOAD, INSTALL }

    /**
     * The progress of the whole operation, 0-100: downloading takes the first third, installing the rest.
     * An operation without downloads (a removal) starts at a third.
     */
    public int overall() {
        return phase == Phase.DOWNLOAD ? percent / 3 : 33 + percent * 67 / 100;
    }
}
