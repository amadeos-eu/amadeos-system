package eu.amadeos.system.software.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.packages.AptPackageService;
import eu.amadeos.system.packages.PackageService;
import eu.amadeos.system.software.appstream.AppStream;
import eu.amadeos.system.software.appstream.AppData;
import eu.amadeos.system.software.appstream.AppStreamSource;
import eu.amadeos.system.software.appstream.InstalledApps;
import eu.amadeos.system.software.ui.ScreenshotCache;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** amadeos-software bindings; the executor, theme and language come from the CommonsModule. */
public final class SoftwareModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "eu.amadeos.system.software.messages";

    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(SoftwareModule.class, MESSAGES, locale);
    }

    @Provides
    @Singleton
    PackageService packages() {
        return new AptPackageService();
    }

    @Provides
    @Singleton
    AppStreamSource appStream(Locale locale) {
        // The repositories' AppStream data first; packages it does not know (the amade applications, a .deb
        // installed by hand) get what their installed desktop entry and metainfo say.
        return () -> {
            Map<String, AppData> apps = new HashMap<>(AppStream.read(AppStream.LISTS, AppStream.ICONS, locale));
            new InstalledApps(locale, InstalledApps.ICONS, InstalledApps.PIXMAPS)
                    .read(InstalledApps.dpkg(), InstalledApps.APPLICATIONS, InstalledApps.METAINFO)
                    .forEach(apps::putIfAbsent);
            return apps;
        };
    }

    @Provides
    @Singleton
    ScreenshotCache screenshots() {
        return ScreenshotCache.inUserCache();
    }
}
