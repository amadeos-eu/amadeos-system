package org.datazuul.amadeos.dm.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.datazuul.amadeos.commons.i18n.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoginLanguageTest {
    @TempDir
    Path home;

    private Path localeConf() throws Exception {
        Path dir = home.resolve(".config/amadeos");
        Files.createDirectories(dir);
        return dir.resolve("locale.conf");
    }

    @Test
    void theUsersLastLanguageIsPreselected() throws Exception {
        assertEquals(Optional.empty(), LoginLanguage.ofUser(home));
        Files.writeString(localeConf(), "es_MX.UTF-8\n");
        assertEquals(Optional.of(Language.SPANISH), LoginLanguage.ofUser(home));
    }

    @Test
    void garbageAndLinksAreIgnored() throws Exception {
        Files.writeString(localeConf(), "x".repeat(10_000));
        assertEquals(Optional.empty(), LoginLanguage.ofUser(home));

        Path elsewhere = home.resolve("elsewhere");
        Files.writeString(elsewhere, "fr_FR.UTF-8");
        Files.delete(localeConf());
        Files.createSymbolicLink(localeConf(), elsewhere);
        assertEquals(Optional.empty(), LoginLanguage.ofUser(home), "the greeter is root; links are not followed");
    }

    @Test
    void theSystemDefaultComesFromEtcDefaultLocale() {
        assertEquals(Language.GERMAN, LoginLanguage.systemDefault(
                Map.of("LANG", "de_DE.UTF-8", "LC_ALL", "de_DE.UTF-8", "LANGUAGE", "de_DE:de:en_US:en")));
        assertEquals(Language.ENGLISH, LoginLanguage.systemDefault(Map.of()));
    }
}
