package eu.amadeos.system.dm.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Optional;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleResolution;
import eu.amadeos.commons.i18n.LocaleStore;

/**
 * Which language the greeter preselects: the one the user had last time (their
 * {@code ~/.config/amadeos/locale.conf}, written at login and by the panel's language switch), otherwise
 * the system default from {@code /etc/default/locale}, otherwise English.
 */
public final class LoginLanguage {
    /** locale.conf holds one short line; never read more of a file the user controls. */
    private static final int MAX_BYTES = 64;

    private LoginLanguage() {
    }

    /** The system default: the display manager's environment, which sources {@code /etc/default/locale}. */
    public static Language systemDefault(Map<String, String> environment) {
        return LocaleResolution.resolve(Optional.empty(), Optional.empty(), environment).language();
    }

    /**
     * The language in the user's {@code locale.conf}. The greeter runs as root and the file belongs to the
     * user, so symbolic links are not followed and only the first bytes are read.
     */
    public static Optional<Language> ofUser(Path home) {
        Path file = home.resolve(".config").resolve("amadeos").resolve(LocaleStore.LOCALE_FILE);
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                return Optional.empty();
            }
            byte[] head;
            try (InputStream in = Files.newInputStream(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                head = in.readNBytes(MAX_BYTES);
            }
            String line = new String(head, StandardCharsets.UTF_8).lines().findFirst().orElse("");
            return Language.parseLocale(line).flatMap(Language::of);
        } catch (IOException | SecurityException e) {
            return Optional.empty();
        }
    }
}
