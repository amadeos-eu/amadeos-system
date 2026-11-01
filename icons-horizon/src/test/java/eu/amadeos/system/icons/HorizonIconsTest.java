package eu.amadeos.system.icons;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorizonIconsTest {

    @Test
    void namesAreUnique() {
        Set<String> seen = new HashSet<>();
        for (HorizonIcons.Entry e : HorizonIcons.entries()) {
            for (String name : e.names()) assertTrue(seen.add(e.context() + "/" + name), "twice: " + name);
        }
        assertTrue(HorizonIcons.names().containsAll(Set.of("folder", "user-home", "amade-pad", "utilities-terminal")));
    }

    @Test
    void everyIconPaintsSomethingAtTheSmallestAndLargestSize() {
        for (HorizonIcons.Entry e : HorizonIcons.entries()) {
            for (int size : new int[] {16, 128}) {
                BufferedImage image = e.paint(size);
                assertEquals(size, image.getWidth());
                int opaque = 0;
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) if ((image.getRGB(x, y) >>> 24) > 200) opaque++;
                }
                assertTrue(opaque > size * size / 5, e.names().getFirst() + " is nearly empty at " + size);
            }
        }
    }

    @Test
    void indexThemeListsEveryFolderAndFallsBackToNsCde() {
        String index = HorizonIcons.indexTheme();
        assertTrue(index.contains("Inherits=NsCDE,hicolor"));
        for (int size : HorizonIcons.SIZES) {
            for (String context : HorizonIcons.CONTEXTS) {
                assertTrue(index.contains("[" + size + "x" + size + "/" + context + "]"), size + " " + context);
            }
        }
    }

    @Test
    void writesPicturesAndLinksForTheOtherNames(@TempDir Path dir) throws Exception {
        int files = HorizonIcons.write(dir);
        assertEquals(HorizonIcons.SIZES.length * HorizonIcons.entries().stream().mapToInt(e -> e.names().size()).sum(),
                files);
        assertTrue(Files.isRegularFile(dir.resolve("index.theme")));
        HorizonIcons.Entry alias = HorizonIcons.entries().stream().filter(e -> e.names().size() > 1).findFirst()
                .orElseThrow();
        Path link = dir.resolve("48x48").resolve(alias.context()).resolve(alias.names().get(1) + ".png");
        assertTrue(Files.isSymbolicLink(link));
        assertEquals(Path.of(alias.names().getFirst() + ".png"), Files.readSymbolicLink(link));
        assertTrue(Files.size(link) > 0);
    }
}
