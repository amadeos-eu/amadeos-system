package eu.amadeos.system.software.appstream;

import java.util.Map;

/** Where the AppStream data comes from: the system's ({@link AppStream#read}), or a test's. */
@FunctionalInterface
public interface AppStreamSource {
    /** The desktop applications by package; empty when there is no AppStream data. */
    Map<String, AppData> applications();
}
