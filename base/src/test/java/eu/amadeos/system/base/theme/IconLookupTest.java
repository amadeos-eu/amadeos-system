package eu.amadeos.system.base.theme;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/** Icons of installed programs: hicolor spread over several folders, SVG and XPM (issue #106). */
class IconLookupTest {
    private static final int RED = 0xFFFF0000;

    @TempDir
    Path home;
    private String oldHome;

    @BeforeEach
    void setUp() {
        oldHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        IconManager.clearCache();
    }

    @AfterEach
    void tearDown() {
        System.setProperty("user.home", oldHome);
        IconManager.clearCache();
    }

    @Test
    void themeIsSearchedInEveryBaseFolderThatHasIt() throws Exception {
        // A user hicolor folder with other icons must not hide the icons in a later hicolor folder.
        Path first = home.resolve(".local/share/icons/hicolor/48x48/apps");
        Files.createDirectories(first);
        writePng(first.resolve("someone-else.png"), 0xFF00FF00);
        Path second = home.resolve(".icons/hicolor/48x48/apps");
        Files.createDirectories(second);
        writePng(second.resolve("probe-raster-app.png"), RED);

        assertEquals(RED, centre(IconManager.getIcon("hicolor", "probe-raster-app", 48)));
    }

    @Test
    void scalableSvgIconIsDrawnAtTheRequestedSize() throws Exception {
        Path dir = home.resolve(".icons/hicolor/scalable/apps");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("probe-svg-app.svg"),
                "<svg xmlns='http://www.w3.org/2000/svg' width='64' height='64' viewBox='0 0 64 64'>"
                        + "<rect width='64' height='64' fill='#ff0000'/></svg>");

        Icon icon = IconManager.getIcon("hicolor", "probe-svg-app", 24);
        assertEquals(24, icon.getIconWidth());
        assertEquals(RED, centre(icon));
    }

    @Test
    void xpmPictureIsRead() {
        BufferedImage img = XpmReader.parse("""
                /* XPM */
                static char *probe[] = {
                /* columns rows colors chars-per-pixel */
                "3 2 3 1 ",
                "  c None",
                ". c #FF0000",
                "X c #00f",
                " .X",
                "X. "
                };
                """);
        assertNotNull(img);
        assertEquals(3, img.getWidth());
        assertEquals(2, img.getHeight());
        assertEquals(0, img.getRGB(0, 0));
        assertEquals(RED, img.getRGB(1, 0));
        assertEquals(0xFF0000FF, img.getRGB(2, 0));
        assertEquals(0xFF0000FF, img.getRGB(0, 1));
    }

    @Test
    void xpmColoursOfEveryDepthAndNames() {
        assertEquals(0xFFFF0000, XpmReader.color(" c #FFFF00000000"));
        assertEquals(0xFF000000, XpmReader.color(" s border c black"));
        assertEquals(0, XpmReader.color(" c none"));
        assertEquals(0xFFBEBEBE, XpmReader.color(" c gray"));
    }

    @Test
    void exactSizeComesFirstThenScalableThenNearest() {
        List<String> order = IconManager.orderDirectories(
                List.of("256x256/apps", "scalable/apps", "16x16/actions", "16x16/apps", "32x32/apps"), 16);
        assertEquals(List.of("16x16/apps", "16x16/actions", "scalable/apps", "32x32/apps", "256x256/apps"), order);
    }

    private static void writePng(Path file, int argb) throws Exception {
        BufferedImage img = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 48; y++) {
            for (int x = 0; x < 48; x++) {
                img.setRGB(x, y, argb);
            }
        }
        ImageIO.write(img, "png", file.toFile());
    }

    private static int centre(Icon icon) {
        BufferedImage img = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        icon.paintIcon(null, g, 0, 0);
        g.dispose();
        return img.getRGB(img.getWidth() / 2, img.getHeight() / 2);
    }
}
