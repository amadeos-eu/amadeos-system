package org.datazuul.amadeos.sysinfo.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.datazuul.amadeos.commons.i18n.Messages;
import org.datazuul.amadeos.sysinfo.service.LocalSources;
import org.datazuul.amadeos.sysinfo.service.SystemProbe;

import java.nio.file.Path;
import java.util.Locale;

/** amade-sysinfo bindings; the executor, theme and language come from the CommonsModule. */
public final class SysinfoModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "org.datazuul.amadeos.sysinfo.messages";

    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(SysinfoModule.class, MESSAGES, locale);
    }

    @Provides
    @Singleton
    SystemProbe probe(Messages msg) {
        return new SystemProbe(Path.of("/"), new LocalSources(), msg);
    }
}
