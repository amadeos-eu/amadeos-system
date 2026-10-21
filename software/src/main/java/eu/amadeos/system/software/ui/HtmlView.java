package eu.amadeos.system.software.ui;

import org.xhtmlrenderer.simple.FSScrollPane;
import org.xhtmlrenderer.simple.XHTMLPanel;
import org.xhtmlrenderer.simple.extend.XhtmlNamespaceHandler;
import org.xhtmlrenderer.swing.BasicPanel;
import org.xhtmlrenderer.swing.FSMouseListener;
import org.xhtmlrenderer.swing.LinkListener;
import org.xhtmlrenderer.swing.NaiveUserAgent;

import javax.swing.JPanel;
import javax.swing.ScrollPaneConstants;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * XHTML rendered with Flying Saucer, as in amade-web: CSS 2.1, no scripts, and only local files (icons, cached
 * screenshots), never the network. A clicked link goes to {@code links}.
 */
final class HtmlView extends JPanel {
    private final XHTMLPanel panel = new XHTMLPanel(new LocalUserAgent());

    HtmlView(Consumer<String> links) {
        super(new BorderLayout());
        for (FSMouseListener listener : new ArrayList<>(panel.getMouseTrackingListeners())) {
            if (listener instanceof LinkListener) panel.removeMouseTrackingListener(listener);
        }
        panel.addMouseTrackingListener(new LinkListener() {
            @Override
            public void linkClicked(BasicPanel source, String uri) {
                links.accept(uri);
            }
        });
        Color background = UIManager.getColor("Panel.background");
        if (background != null) panel.setBackground(background);
        FSScrollPane scroll = new FSScrollPane(panel);
        scroll.setBorder(null);
        // The page is laid out to the pane's width.
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        add(scroll, BorderLayout.CENTER);
    }

    void show(String xhtml) {
        panel.setDocumentFromString(xhtml, "file:///", new XhtmlNamespaceHandler());
    }

    /** Loads files and embedded data only. */
    private static final class LocalUserAgent extends NaiveUserAgent {
        @Override
        protected InputStream openStream(String uri) throws IOException {
            String lower = uri.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("file:") && !lower.startsWith("jar:")) {
                throw new IOException("Not loading remote resource " + uri);
            }
            return super.openStream(uri);
        }
    }
}
