package eu.amadeos.system.software.ui;

import eu.amadeos.system.packages.CatalogEntry;
import eu.amadeos.system.packages.Category;
import eu.amadeos.system.packages.PackageInfo;
import eu.amadeos.system.software.appstream.AppData;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static eu.amadeos.system.software.ui.UpdateTableModelTest.EN;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetailsPageTest {
    private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    private static final CatalogEntry GIMP = new CatalogEntry("gimp", "GNU Image Manipulation Program", "graphics",
            Category.APPLICATION, "", "2.10", false);
    private static final PackageInfo INFO = new PackageInfo("gimp", "2.10", "amd64", "graphics", "summary",
            "Plain Debian text.", 20_000, 5_000_000, "https://www.gimp.org/", List.of(), List.of());

    private static String page(Item item, DetailsPage.Shot shot) {
        return DetailsPage.build(EN, item, Optional.of(INFO), "Not installed.", shot, FONT, Color.BLACK, Color.BLUE);
    }

    @Test
    void aPackageWithoutAppStreamData() {
        String html = page(Item.of(GIMP), DetailsPage.NO_SHOT);
        assertTrue(html.contains("<h1>gimp</h1>"), html);
        assertFalse(html.contains("Package gimp"), "the title is the package name already");
        assertTrue(html.contains("<p>Plain Debian text.</p>"));
        assertTrue(html.replace(',', '.').contains("Download 5.0 MB. 20.5 MB on disk"), html);
        assertTrue(html.contains("<a href='https://www.gimp.org/'>Home page</a>"));
    }

    @Test
    void anApplicationWithIconScreenshotAndItsOwnDescription() {
        AppData app = new AppData("org.gimp.GIMP", "gimp", "GNU Image <Manipulation>", "Create images",
                "<p>GIMP is <em>free</em>.</p>", List.of(), Optional.of(Path.of("/icons/gimp.png")), "", List.of());
        Item item = new Item(GIMP, Optional.of(app));
        String html = page(item, new DetailsPage.Shot.Shown(Path.of("/cache/shot.png"), 400, 225, "Painting"));
        assertTrue(html.contains("<h1>GNU Image &lt;Manipulation&gt;</h1>"), html);
        assertTrue(html.contains("Package gimp"));
        assertTrue(html.contains("<img src='file:///icons/gimp.png' width='48' height='48'"));
        assertTrue(html.contains("<img src='file:///cache/shot.png' width='400' height='225'"));
        assertTrue(html.contains("<div class='caption'>Painting</div>"));
        assertTrue(html.contains("<p>GIMP is <em>free</em>.</p>"));
        assertFalse(html.contains("Plain Debian text."), "AppStream's description wins");

        assertTrue(page(item, new DetailsPage.Shot.Loading()).contains("Loading the screenshot…"));
        assertTrue(page(item, new DetailsPage.Shot.Failed()).contains("The screenshot could not be loaded."));
    }
}
