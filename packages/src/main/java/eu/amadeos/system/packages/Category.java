package eu.amadeos.system.packages;

/** The tabs of the Software program a package is listed in. */
public enum Category {
    /** Programs with a window and a menu entry. */
    APPLICATION,
    /** Programs for the terminal, and services. */
    COMMAND_LINE,
    /** Code other programs use: shared libraries, development files, language modules. */
    LIBRARY,
    /** Fonts, documentation, translations, firmware, kernels, data, metapackages. */
    OTHER
}
