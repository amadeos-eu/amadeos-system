package eu.amadeos.system.software.appstream;

import eu.amadeos.system.packages.Categories;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * The applications of the installed packages, for packages that are in no repository's AppStream data (the
 * amade applications and games, a .deb installed by hand): name, summary, icon and categories from the
 * package's desktop entry in {@code /usr/share/applications}, its icon from the hicolor theme or
 * {@code /usr/share/pixmaps}; a description, home page and screenshots when the package ships AppStream
 * metadata in {@code /usr/share/metainfo}.
 */
public final class InstalledApps {
    private static final Logger LOG = LoggerFactory.getLogger(InstalledApps.class);
    public static final Path APPLICATIONS = Path.of("/usr/share/applications");
    public static final Path METAINFO = Path.of("/usr/share/metainfo");
    public static final Path ICONS = Path.of("/usr/share/icons/hicolor");
    public static final Path PIXMAPS = Path.of("/usr/share/pixmaps");
    /** Icon sizes to look for, the one nearest the 64 pixels AppStream's cached icons have first. */
    private static final List<String> SIZES = List.of("64x64", "48x48", "96x96", "128x128", "256x256", "32x32");

    /** Which package owns the files of a pattern ({@code dpkg-query -S}); empty when none does. */
    @FunctionalInterface
    public interface Owners {
        List<Categories.Owned> of(String pattern);
    }

    private final Locale locale;
    private final Path icons;
    private final Path pixmaps;

    public InstalledApps(Locale locale, Path icons, Path pixmaps) {
        this.locale = locale;
        this.icons = icons;
        this.pixmaps = pixmaps;
    }

    /** The applications by package, from the desktop entries and metainfo files {@code owners} names. */
    public Map<String, AppData> read(Owners owners, Path applications, Path metainfo) {
        Map<String, AppData> apps = new HashMap<>();
        for (Categories.Owned owned : owners.of(applications + "/*.desktop")) {
            Path file = Path.of(owned.path());
            if (!file.startsWith(applications)) continue;
            desktopEntry(file, owned.pkg()).ifPresent(app -> apps.putIfAbsent(app.pkg(), app));
        }
        for (Categories.Owned owned : owners.of(metainfo + "/*.xml")) {
            Path file = Path.of(owned.path());
            if (!file.startsWith(metainfo)) continue;
            metainfo(file, owned.pkg()).ifPresent(app -> apps.merge(app.pkg(), app, InstalledApps::combine));
        }
        return apps;
    }

    /** The metainfo's texts and screenshots; the desktop entry's where the metainfo has none. */
    static AppData combine(AppData desktop, AppData meta) {
        return new AppData(meta.id(), meta.pkg(), or(meta.name(), desktop.name()), or(meta.summary(), desktop.summary()),
                meta.description(), meta.categories().isEmpty() ? desktop.categories() : meta.categories(),
                meta.icon().or(desktop::icon), meta.homepage(), meta.screenshots());
    }

    // ---- desktop entries ---------------------------------------------------------------------------

    /** A shown application's desktop entry; hidden ones, links and directories are left out. */
    Optional<AppData> desktopEntry(Path file, String pkg) {
        Map<String, String> entry = new LinkedHashMap<>();
        try {
            boolean inEntry = false;
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String text = line.strip();
                if (text.startsWith("[")) {
                    inEntry = text.equals("[Desktop Entry]");
                    continue;
                }
                int equals = text.indexOf('=');
                if (!inEntry || text.startsWith("#") || equals <= 0) continue;
                entry.putIfAbsent(text.substring(0, equals).strip(), text.substring(equals + 1).strip());
            }
        } catch (IOException | RuntimeException e) {
            LOG.debug("Cannot read {}", file, e);
            return Optional.empty();
        }
        if (!"Application".equals(entry.get("Type")) || "true".equals(entry.get("NoDisplay"))
                || "true".equals(entry.get("Hidden"))) {
            return Optional.empty();
        }
        String name = localizedKey(entry, "Name");
        if (name.isEmpty()) return Optional.empty();
        String summary = or(localizedKey(entry, "Comment"), localizedKey(entry, "GenericName"));
        List<String> categories = Arrays.stream(entry.getOrDefault("Categories", "").split(";"))
                .map(String::strip).filter(c -> !c.isEmpty()).toList();
        String id = file.getFileName().toString().replaceFirst("\\.desktop$", "");
        return Optional.of(new AppData(id, pkg, name, summary, "", categories,
                icon(entry.getOrDefault("Icon", "")), "", List.of()));
    }

    /** {@code Key[de_DE]}, then {@code Key[de]}, then {@code Key}. */
    private String localizedKey(Map<String, String> entry, String key) {
        String language = locale.getLanguage();
        for (String candidate : List.of(key + "[" + language + "_" + locale.getCountry() + "]",
                key + "[" + language + "]", key)) {
            String value = entry.get(candidate);
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    /** An icon Swing can show (PNG): an absolute path, or the name in hicolor or pixmaps. */
    Optional<Path> icon(String name) {
        if (name.isBlank()) return Optional.empty();
        if (name.startsWith("/")) {
            Path file = Path.of(name);
            return name.endsWith(".png") && Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
        }
        String file = name.endsWith(".png") ? name : name + ".png";
        for (String size : SIZES) {
            Path candidate = icons.resolve(size).resolve("apps").resolve(file);
            if (Files.isRegularFile(candidate)) return Optional.of(candidate);
        }
        Path pixmap = pixmaps.resolve(file);
        return Files.isRegularFile(pixmap) ? Optional.of(pixmap) : Optional.empty();
    }

    // ---- metainfo ----------------------------------------------------------------------------------

    /** A desktop application's metainfo file; other components (fonts, add-ons) are left out. */
    Optional<AppData> metainfo(Path file, String pkg) {
        Element component;
        try (InputStream in = Files.newInputStream(file)) {
            component = parser().parse(in).getDocumentElement();
        } catch (IOException | SAXException | ParserConfigurationException | RuntimeException e) {
            LOG.debug("Cannot read {}", file, e);
            return Optional.empty();
        }
        String type = component.getAttribute("type");
        if (!component.getTagName().equals("component") || !(type.equals("desktop-application")
                || type.equals("desktop"))) {
            return Optional.empty();
        }
        String id = text(first(component, "id"));
        String homepage = "";
        for (Element url : children(component, "url")) {
            if (url.getAttribute("type").equals("homepage")) homepage = text(url);
        }
        List<String> categories = new ArrayList<>();
        Element categoryList = first(component, "categories");
        if (categoryList != null) {
            for (Element category : children(categoryList, "category")) categories.add(text(category));
        }
        Optional<Path> icon = Optional.empty();
        for (Element element : children(component, "icon")) {
            if (element.getAttribute("type").equals("stock")) icon = icon.or(() -> icon(text(element)));
        }
        return Optional.of(new AppData(id, pkg, localized(component, "name"), localized(component, "summary"),
                description(first(component, "description")), categories, icon, homepage, screenshots(component)));
    }

    /** The element's text in the locale (xml:lang), else the untranslated one. */
    private String localized(Element parent, String tag) {
        Map<String, String> byLanguage = new HashMap<>();
        for (Element element : children(parent, tag)) {
            byLanguage.putIfAbsent(element.getAttributeNS(XMLConstants.XML_NS_URI, "lang"), text(element));
        }
        String language = locale.getLanguage();
        for (String key : List.of(language + "_" + locale.getCountry(), language, "")) {
            String value = byLanguage.get(key);
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    /**
     * The description as AppStream markup, its paragraphs and lists in the locale where they are translated
     * (metainfo translates each {@code <p>} and {@code <li>} with xml:lang), else the untranslated ones.
     */
    private String description(Element description) {
        if (description == null) return "";
        String language = locale.getLanguage();
        boolean translated = hasLanguage(description, language);
        StringBuilder markup = new StringBuilder();
        append(markup, description, translated ? language : "", true);
        return markup.toString().strip();
    }

    private static boolean hasLanguage(Element element, String language) {
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element e && (lang(e).equals(language) || lang(e).startsWith(language + "_")
                    || hasLanguage(e, language))) {
                return true;
            }
        }
        return false;
    }

    private static void append(StringBuilder markup, Element element, String language, boolean root) {
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element e) {
                String lang = lang(e);
                boolean matches = language.isEmpty() ? lang.isEmpty()
                        : lang.equals(language) || lang.startsWith(language + "_")
                        // a list itself is untranslated, its items are
                        || (lang.isEmpty() && (e.getTagName().equals("ul") || e.getTagName().equals("ol")));
                if (!matches) continue;
                markup.append('<').append(e.getTagName()).append('>');
                append(markup, e, language, false);
                markup.append("</").append(e.getTagName()).append('>');
            } else if (!root && child.getNodeType() == Node.TEXT_NODE && !isList(element)) {
                // Line breaks and indentation of the file are no part of the text.
                markup.append(escape(child.getNodeValue().replaceAll("\\s+", " ")));
            }
        }
    }

    private static boolean isList(Element element) {
        return element.getTagName().equals("ul") || element.getTagName().equals("ol");
    }

    private static String lang(Element element) {
        return element.getAttributeNS(XMLConstants.XML_NS_URI, "lang");
    }

    /** The screenshots with an http(s) image, the default one first; the source image or first thumbnail. */
    private List<AppData.Screenshot> screenshots(Element component) {
        Element list = first(component, "screenshots");
        if (list == null) return List.of();
        List<AppData.Screenshot> shots = new ArrayList<>();
        for (Element shot : children(list, "screenshot")) {
            URI image = null;
            for (Element candidate : children(shot, "image")) {
                URI uri = http(text(candidate));
                if (uri != null && (image == null || candidate.getAttribute("type").equals("source"))) image = uri;
            }
            if (image == null) continue;
            AppData.Screenshot screenshot = new AppData.Screenshot(localized(shot, "caption"), image);
            if (shot.getAttribute("type").equals("default")) {
                shots.addFirst(screenshot);
            } else {
                shots.add(screenshot);
            }
        }
        return shots;
    }

    private static URI http(String url) {
        try {
            URI uri = URI.create(url.strip());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return scheme.equals("https") || scheme.equals("http") ? uri : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---- helpers -----------------------------------------------------------------------------------

    /** A namespace-aware parser that refuses doctypes, so no entity or external file is ever read. */
    private static DocumentBuilder parser() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setExpandEntityReferences(false);
        factory.setXIncludeAware(false);
        return factory.newDocumentBuilder();
    }

    private static List<Element> children(Element parent, String tag) {
        List<Element> found = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element e && e.getTagName().equals(tag)) found.add(e);
        }
        return found;
    }

    private static Element first(Element parent, String tag) {
        List<Element> found = children(parent, tag);
        return found.isEmpty() ? null : found.getFirst();
    }

    private static String text(Element element) {
        return element == null ? "" : element.getTextContent().strip().replaceAll("\\s+", " ");
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String or(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** The system's owners: {@code dpkg-query -S}, which fails (and gives nothing) when no file matches. */
    public static Owners dpkg() {
        return pattern -> {
            try {
                Process process = new ProcessBuilder("dpkg-query", "-S", pattern)
                        .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (!process.waitFor(30, TimeUnit.SECONDS)) process.destroy();
                return Categories.parseOwners(output);
            } catch (IOException e) {
                LOG.debug("Cannot run dpkg-query", e);
                return List.of();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return List.of();
            }
        };
    }
}
