package eu.amadeos.system.software.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.packages.AptPackageService;
import eu.amadeos.system.packages.PackageService;
import eu.amadeos.system.software.appstream.AppStream;
import eu.amadeos.system.software.appstream.AppStreamSource;
import eu.amadeos.system.software.ui.ScreenshotCache;

import java.util.Locale;

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
        return () -> AppStream.read(AppStream.LISTS, AppStream.ICONS, locale);
    }

    @Provides
    @Singleton
    ScreenshotCache screenshots() {
        return ScreenshotCache.inUserCache();
    }
}
