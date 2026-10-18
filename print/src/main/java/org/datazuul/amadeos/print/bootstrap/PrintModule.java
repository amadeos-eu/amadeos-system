package org.datazuul.amadeos.print.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.datazuul.amadeos.commons.i18n.Messages;
import org.datazuul.amadeos.print.cups.CupsClient;
import org.datazuul.amadeos.print.ipp.IppTransport;

import java.util.Locale;

/** amadeos-print bindings; the executor, theme and language come from the CommonsModule. */
public final class PrintModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "org.datazuul.amadeos.print.messages";

    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(PrintModule.class, MESSAGES, locale);
    }

    @Provides
    @Singleton
    CupsClient cups(Locale locale) {
        String user = System.getProperty("user.name", "");
        return new CupsClient(IppTransport.local(user), user, locale);
    }
}
