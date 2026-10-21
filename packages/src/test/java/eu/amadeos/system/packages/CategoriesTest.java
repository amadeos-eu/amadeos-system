package eu.amadeos.system.packages;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CategoriesTest {
    private static final Categories.Hints APPSTREAM = new Categories.Hints(true, Set.of("gimp", "libreoffice-writer"),
            Set.of("ripgrep", "libgd-dev"));

    @Test
    void appStreamAndInstalledFilesComeFirst() {
        // A desktop application, even with a name like a library.
        assertEquals(Category.APPLICATION, Categories.of("libreoffice-writer", "editors", List.of(), APPSTREAM));
        assertEquals(Category.COMMAND_LINE, Categories.of("ripgrep", "rust", List.of(), APPSTREAM));
        // Development files stay libraries, though they bring a *-config program.
        assertEquals(Category.LIBRARY, Categories.of("libgd-dev", "libdevel", List.of(), APPSTREAM));
        // With AppStream data, an x11 package it does not name is not an application.
        assertEquals(Category.OTHER, Categories.of("xserver-xorg-core", "x11", List.of(), APPSTREAM));
    }

    @Test
    void withoutHintsTheSectionAndTagsDecide() {
        Categories.Hints none = Categories.Hints.NONE;
        assertEquals(Category.LIBRARY, Categories.of("libpcre2-8-0", "libs", List.of(), none));
        assertEquals(Category.LIBRARY, Categories.of("python3-requests", "python", List.of(), none));
        assertEquals(Category.LIBRARY, Categories.of("zlib1g-dev", "devel", List.of(), none));
        assertEquals(Category.LIBRARY, Categories.of("foo-dbgsym", "debug", List.of(), none));
        assertEquals(Category.COMMAND_LINE, Categories.of("unrar", "utils", List.of(), none));
        assertEquals(Category.COMMAND_LINE, Categories.of("gcc", "devel", List.of(), none));
        assertEquals(Category.COMMAND_LINE, Categories.of("htop", "misc", List.of("interface::commandline"), none));
        assertEquals(Category.APPLICATION, Categories.of("gimp", "graphics", List.of(), none));
        assertEquals(Category.APPLICATION, Categories.of("viewer", "misc", List.of("interface::x11"), none));
        assertEquals(Category.OTHER, Categories.of("fonts-dejavu", "fonts", List.of(), none));
        assertEquals(Category.OTHER, Categories.of("linux-image-amd64", "kernel", List.of(), none));
        assertEquals(Category.OTHER, Categories.of("manpages", "doc", List.of(), none));
        // What comes with a program is not a program of its own.
        assertEquals(Category.OTHER, Categories.of("0ad-data", "games", List.of(), none));
        assertEquals(Category.OTHER, Categories.of("git-doc", "doc", List.of(), none));
        assertEquals(Category.OTHER, Categories.of("vim-common", "editors", List.of(), none));
    }

    @Test
    void dep11NamesTheApplications() {
        Categories.Dep11Reader reader = new Categories.Dep11Reader();
        """
                ---
                File: DEP-11
                Origin: debian-trixie-main
                ---
                Type: desktop-application
                ID: org.gimp.GIMP
                Package: gimp
                Name:
                  C: GNU Image Manipulation Program
                Provides:
                  binaries:
                  - gimp
                ---
                Type: console-application
                ID: ripgrep
                Package: ripgrep
                ---
                Type: font
                Package: fonts-dejavu-core
                ---
                Type: desktop-application
                Package: inkscape""".lines().forEach(reader);
        assertEquals(Set.of("gimp", "inkscape"), reader.desktop());
        assertEquals(Set.of("ripgrep"), reader.console());
    }

    @Test
    void ownersOfFiles() {
        String output = """
                coreutils: /usr/bin/ls
                diversion by dash from: /bin/sh
                libc-bin, libc6:amd64: /usr/bin/ldd
                """;
        assertEquals(List.of(new Categories.Owned("coreutils", "/usr/bin/ls"),
                new Categories.Owned("libc-bin", "/usr/bin/ldd"), new Categories.Owned("libc6", "/usr/bin/ldd")),
                Categories.parseOwners(output));
    }
}
