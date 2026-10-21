package eu.amadeos.system.software.appstream;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * What AppStream says about the application in a package, in the user's language where it has a translation.
 *
 * @param id          the component id ({@code org.gimp.GIMP})
 * @param pkg         the package that brings it
 * @param name        the application's name ("GNU Image Manipulation Program")
 * @param summary     one line on what it does
 * @param description the long description as AppStream markup ({@code <p>}, {@code <ul>}, {@code <ol>},
 *                    {@code <li>}, {@code <em>}, {@code <code>}), empty when there is none
 * @param categories  freedesktop menu categories (Graphics, Office, …)
 * @param icon        the cached 64x64 icon, when apt fetched the icons
 * @param homepage    the project's home page, empty when unknown
 * @param screenshots the screenshots, the default one first
 */
public record AppData(String id, String pkg, String name, String summary, String description,
                      List<String> categories, Optional<Path> icon, String homepage, List<Screenshot> screenshots) {

    public AppData {
        categories = List.copyOf(categories);
        screenshots = List.copyOf(screenshots);
    }

    /**
     * One screenshot.
     *
     * @param caption what it shows, may be empty
     * @param image   an image of about the details pane's width, on the distribution's media server
     */
    public record Screenshot(String caption, URI image) {
    }
}
