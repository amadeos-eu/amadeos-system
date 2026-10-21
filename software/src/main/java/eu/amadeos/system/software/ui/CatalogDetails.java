package eu.amadeos.system.software.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.packages.CatalogEntry;
import eu.amadeos.system.packages.PackageInfo;
import eu.amadeos.system.software.appstream.AppData;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.net.URI;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The right side of a catalog tab: what the chosen package is, whether and how it is installed, the
 * application's screenshot and description, rendered as XHTML; Install, Remove or Update below.
 */
final class CatalogDetails extends JPanel {
    /** Loads a screenshot in the background and answers on the event dispatch thread. */
    interface ScreenshotLoader {
        void load(URI image, Consumer<Path> done, Consumer<Throwable> failed);
    }

    private final Messages msg;
    private final ScreenshotLoader screenshots;
    private final HtmlView view;
    private final JButton install;
    private final JButton remove;
    private final JButton update;
    private final Timer relayout;
    private Item item;
    private Optional<PackageInfo> info = Optional.empty();
    private DetailsPage.Shot shot = DetailsPage.NO_SHOT;
    private Path shotFile;
    private String state = "";
    private boolean busy;

    CatalogDetails(Messages msg, ScreenshotLoader screenshots, Consumer<String> links,
                   Consumer<CatalogEntry> onInstall, Consumer<CatalogEntry> onRemove, Consumer<CatalogEntry> onUpdate) {
        super(new BorderLayout(0, 8));
        this.msg = msg;
        this.screenshots = screenshots;
        this.view = new HtmlView(links);
        setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 10));

        install = new JButton(msg.get("action.install"));
        install.addActionListener(e -> onInstall.accept(item.entry()));
        remove = new JButton(msg.get("action.remove"));
        remove.addActionListener(e -> onRemove.accept(item.entry()));
        update = new JButton(msg.get("action.update"));
        update.addActionListener(e -> onUpdate.accept(item.entry()));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(remove);
        buttons.add(update);
        buttons.add(install);

        add(view, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        setMinimumSize(new Dimension(260, 200));
        // The screenshot fits the pane's width.
        relayout = new Timer(200, e -> {
            if (shot instanceof DetailsPage.Shot.Shown) showShot(shotFile);
        });
        relayout.setRepeats(false);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                relayout.restart();
            }
        });
        clear();
    }

    void clear() {
        item = null;
        info = Optional.empty();
        shot = DetailsPage.NO_SHOT;
        state = "";
        view.show("<html xmlns='http://www.w3.org/1999/xhtml'><body style='margin: 6px 10px'><p>"
                + Html.escape(msg.get("details.choose")) + "</p></body></html>");
        updateButtons();
    }

    /** The item at once; the repository's description when it has arrived. */
    void show(Item shown, Optional<PackageInfo> newInfo) {
        boolean other = item == null || !item.name().equals(shown.name());
        if (other) {
            info = Optional.empty();
            shot = DetailsPage.NO_SHOT;
            loadShot(shown);
        }
        if (newInfo.isPresent()) info = newInfo;
        item = shown;
        state = stateText(msg, shown.entry());
        render();
        updateButtons();
    }

    private void loadShot(Item shown) {
        Optional<AppData.Screenshot> first = shown.app().flatMap(a -> a.screenshots().stream().findFirst());
        if (first.isEmpty()) return;
        shot = new DetailsPage.Shot.Loading();
        String name = shown.name();
        screenshots.load(first.get().image(), file -> {
            if (item == null || !item.name().equals(name)) return;
            showShot(file);
        }, error -> {
            if (item == null || !item.name().equals(name)) return;
            shot = new DetailsPage.Shot.Failed();
            render();
        });
    }

    private void showShot(Path file) {
        shotFile = file;
        Dimension size = imageSize(file);
        if (size == null) {
            shot = new DetailsPage.Shot.Failed();
        } else {
            int width = Math.min(size.width, Math.max(160, view.getWidth() - 40));
            int height = Math.max(1, size.height * width / Math.max(1, size.width));
            String caption = item.app().flatMap(a -> a.screenshots().stream().findFirst())
                    .map(AppData.Screenshot::caption).orElse("");
            shot = new DetailsPage.Shot.Shown(file, width, height, caption);
        }
        render();
    }

    /** The size of an image without decoding it, or null when it is none. */
    static Dimension imageSize(Path file) {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            if (in == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return new Dimension(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void render() {
        if (item == null) return;
        Font font = UIManager.getFont("Label.font");
        Color text = UIManager.getColor("Label.foreground");
        view.show(DetailsPage.build(msg, item, info, state, shot,
                font != null ? font : new Font(Font.SANS_SERIF, Font.PLAIN, 12),
                text != null ? text : Color.BLACK, new Color(0x1A, 0x4D, 0x99)));
    }

    /** "Installed: version 1.2. Version 1.3 is available." and the like. */
    static String stateText(Messages msg, CatalogEntry e) {
        String text;
        if (!e.installed()) {
            text = msg.format("details.not-installed", e.availableVersion());
        } else if (e.availableVersion().isEmpty()) {
            text = msg.format("details.local", e.installedVersion());
        } else if (e.upgradable()) {
            text = msg.format("details.installed.update", e.installedVersion(), e.availableVersion());
        } else {
            text = msg.format("details.installed", e.installedVersion());
        }
        return e.installed() && e.automatic() ? text + " " + msg.get("details.dependency") : text;
    }

    void setBusy(boolean busy) {
        this.busy = busy;
        updateButtons();
    }

    private void updateButtons() {
        CatalogEntry entry = item == null ? null : item.entry();
        install.setEnabled(!busy && entry != null && !entry.installed() && !entry.availableVersion().isEmpty());
        remove.setEnabled(!busy && entry != null && entry.installed());
        update.setEnabled(!busy && entry != null && entry.upgradable());
        update.setVisible(entry != null && entry.upgradable());
    }

    Item item() {
        return item;
    }

    Optional<PackageInfo> info() {
        return info;
    }

    DetailsPage.Shot shot() {
        return shot;
    }

    String titleText() {
        return item == null ? "" : item.title();
    }

    String stateText() {
        return state;
    }

    JButton installButton() {
        return install;
    }

    JButton removeButton() {
        return remove;
    }

    JButton updateButton() {
        return update;
    }
}
