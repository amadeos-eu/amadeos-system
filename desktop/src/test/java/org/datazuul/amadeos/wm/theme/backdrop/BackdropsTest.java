package org.datazuul.amadeos.wm.theme.backdrop;

import org.datazuul.amadeos.wm.theme.WallpaperManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class BackdropsTest {
    private static final Color SLATE = new Color(0x4A, 0x69, 0x84);

    /** A Concave-like gradient: the four shading symbols, one of them keyed by a backslash as in SkyDark.pm. */
    public static final String SHADED = """
            /* XPM */
            static char * shaded[] = {
            /* width height ncolors cpp [x_hot y_hot] */
            "4 2 4 1 0 0",
            /* colors */
            "\\    s bottomShadowColor m black c #636363636363",
            ".    s selectColor m white c #737373737373",
            "X    s background    m black c #949494949494",
            "o    s topShadowColor m white c #bdbdbdbdbdbd",
            /* pixels */
            "\\.Xo",
            "oX.\\"};
            """;

    static final String ALL_SET = "#define a_width 8\n#define a_height 1\nstatic char a_bits[] = { 0xff };\n";
    static final String ALL_CLEAR = "#define b_width 8\n#define b_height 1\nstatic char b_bits[] = { 0x00 };\n";

    @TempDir
    Path dir;

    @Test
    void symbolicColoursFollowTheBackdropColour() throws Exception {
        Path file = Files.writeString(dir.resolve("Shaded.pm"), SHADED, StandardCharsets.ISO_8859_1);
        MotifColors motif = MotifColors.of(SLATE);
        BufferedImage image = Backdrops.load(file.toString(), SLATE);
        assertEquals(4, image.getWidth());
        assertEquals(2, image.getHeight());
        assertEquals(motif.bottomShadow().getRGB(), image.getRGB(0, 0));
        assertEquals(motif.select().getRGB(), image.getRGB(1, 0));
        assertEquals(motif.background().getRGB(), image.getRGB(2, 0));
        assertEquals(motif.topShadow().getRGB(), image.getRGB(3, 0));
        assertEquals(motif.bottomShadow().getRGB(), image.getRGB(3, 1));

        BufferedImage red = Backdrops.load(file.toString(), new Color(0x90, 0x20, 0x20));
        assertNotEquals(colours(image), colours(red));
    }

    @Test
    void bitmapsUseBackgroundOnBottomShadowLikeDtwm() throws Exception {
        MotifColors motif = MotifColors.of(SLATE);
        Path set = Files.writeString(dir.resolve("Foreground.bm"), ALL_SET);
        Path clear = Files.writeString(dir.resolve("Background.bm"), ALL_CLEAR);
        assertEquals(Set.of(motif.background().getRGB()), colours(Backdrops.load(set.toString(), SLATE)));
        assertEquals(Set.of(motif.bottomShadow().getRGB()), colours(Backdrops.load(clear.toString(), SLATE)));
    }

    @Test
    void systemBackdropsAreFoundInSubfoldersAndSortedByName() throws Exception {
        Path cde = Files.createDirectories(dir.resolve("cde"));
        Files.writeString(cde.resolve("Toronto.bm"), ALL_SET);
        Files.writeString(cde.resolve("Concave.pm"), SHADED);
        Files.writeString(cde.resolve("README"), "not a backdrop");
        Files.writeString(dir.resolve("Pebbles.xpm"), SHADED);
        List<String> names = Backdrops.patterns(dir).stream().map(p -> Backdrops.displayName(p.toString())).toList();
        assertEquals(List.of("Concave", "Pebbles", "Toronto"), names);
        assertTrue(Backdrops.patterns(dir.resolve("missing")).isEmpty());
    }

    /** Runs where the amadeos-backdrops-cde package is installed (or -Damadeos.backdrops.dir points to it). */
    @Test
    void everyInstalledBackdropDecodes() throws Exception {
        List<Path> installed = Backdrops.systemBackdrops();
        assumeFalse(installed.isEmpty(), "amadeos-backdrops-cde not installed");
        for (Path file : installed) {
            BufferedImage image = Backdrops.load(file.toString(), SLATE);
            assertTrue(image.getWidth() > 0 && image.getHeight() > 0, file.toString());
        }
    }

    @Test
    void xbmBitsAreReadLeastSignificantFirst() throws Exception {
        String source = "#define t_width 10\n#define t_height 2\nstatic char t_bits[] = {\n 0x01, 0x02, 0xff, 0x03 };\n";
        BufferedImage image = XbmDecoder.decode(source, Color.WHITE, Color.BLACK);
        int white = Color.WHITE.getRGB();
        int black = Color.BLACK.getRGB();
        assertEquals(white, image.getRGB(0, 0));
        assertEquals(black, image.getRGB(1, 0));
        assertEquals(white, image.getRGB(9, 0)); // second byte, bit 1
        assertEquals(black, image.getRGB(8, 0));
        assertEquals(white, image.getRGB(7, 1));
        assertEquals(white, image.getRGB(9, 1));
    }

    @Test
    void xpmColourKeysAndNamesAreResolved() throws Exception {
        String source = """
                /* XPM */
                static char *t[] = {
                "3 1 3 1",
                "a\tc red",
                "b s selectColor m white c #000000000000",
                "c c None",
                "abc"};
                """;
        BufferedImage plain = XpmDecoder.decode(source, null);
        assertEquals(Color.RED.getRGB(), plain.getRGB(0, 0));
        assertEquals(Color.BLACK.getRGB(), plain.getRGB(1, 0));
        assertEquals(0, plain.getRGB(2, 0) >>> 24, "None is transparent");

        BufferedImage mapped = XpmDecoder.decode(source, s -> s.equals("selectColor") ? Color.GREEN : null);
        assertEquals(Color.GREEN.getRGB(), mapped.getRGB(1, 0));
    }

    @Test
    void x11ColourSpecifications() {
        assertEquals(new Color(0x94, 0x94, 0x94), X11Colors.parse("#949494949494"));
        assertEquals(new Color(0xAB, 0xCD, 0xEF), X11Colors.parse("#ABCDEF"));
        assertEquals(Color.WHITE, X11Colors.parse("#fff"));
        assertEquals(Color.YELLOW, X11Colors.parse("yellow"));
        assertEquals(new Color(127, 127, 127), X11Colors.parse("gray50")); // rgb.txt: 7F
        assertNull(X11Colors.parse("no such colour"));
    }

    @Test
    void motifShadesAreOrderedAroundTheBackground() {
        for (Color bg : new Color[]{SLATE, new Color(20, 20, 30), new Color(240, 240, 235), new Color(0xAE, 0xB2, 0xC3)}) {
            MotifColors m = MotifColors.of(bg);
            assertTrue(lum(m.bottomShadow()) < lum(bg), "bottom shadow darker for " + bg);
            assertTrue(lum(m.topShadow()) != lum(bg), "top shadow differs for " + bg);
        }
        assertEquals(Color.WHITE, MotifColors.of(SLATE).foreground());
        assertEquals(Color.BLACK, MotifColors.of(new Color(240, 240, 235)).foreground());
    }

    @Test
    void wallpaperManagerListsAndLoadsTheSystemBackdrops() throws Exception {
        Path cde = Files.createDirectories(dir.resolve("cde"));
        Path pebbles = Files.writeString(cde.resolve("Pebbles.pm"), SHADED);
        String previous = System.getProperty(Backdrops.DIR_PROPERTY);
        System.setProperty(Backdrops.DIR_PROPERTY, dir.toString());
        try {
            var items = WallpaperManager.availableWallpapers();
            assertTrue(items.stream().anyMatch(i -> i.name().equals("Pebbles") && i.isPattern()
                    && i.path().equals(pebbles.toString())));
            assertTrue(items.stream().anyMatch(i -> i.path().equals(WallpaperManager.NO_BACKDROP)));
            assertNotNull(WallpaperManager.loadImage(pebbles.toString(), SLATE));
            assertNull(WallpaperManager.loadImage(WallpaperManager.NO_BACKDROP, SLATE));
        } finally {
            if (previous == null) System.clearProperty(Backdrops.DIR_PROPERTY);
            else System.setProperty(Backdrops.DIR_PROPERTY, previous);
        }
    }

    private static int lum(Color c) {
        return c.getRed() * 30 + c.getGreen() * 59 + c.getBlue() * 11;
    }

    private static Set<Integer> colours(BufferedImage image) {
        Set<Integer> set = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) set.add(image.getRGB(x, y));
        }
        return set;
    }
}
