package eu.amadeos.system.software.appstream;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.Reader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Reads one AppStream DEP-11 file (YAML, one document per component, a header document first) and keeps the
 * desktop applications. Texts are picked for one locale: {@code de_AT}, then {@code de}, then the untranslated
 * {@code C}.
 */
public final class AppStreamReader {
    /** The screenshot width that fits the details pane; the nearest thumbnail is taken. */
    static final int SCREENSHOT_WIDTH = 624;

    private final Locale locale;
    private final List<Path> iconRoots;

    /**
     * @param iconRoots where appstream unpacks the icons apt fetched, one directory per origin below each
     *                  ({@code /var/lib/swcatalog/icons}, {@code /var/lib/app-info/icons} on older systems)
     */
    public AppStreamReader(Locale locale, List<Path> iconRoots) {
        this.locale = locale;
        this.iconRoots = List.copyOf(iconRoots);
    }

    /** Hands each desktop application of the file to {@code sink}. */
    public void read(Reader yaml, Consumer<AppData> sink) {
        LoaderOptions options = new LoaderOptions();
        // A DEP-11 file of a whole archive is tens of megabytes; the default limit is 3 MB.
        options.setCodePointLimit(Integer.MAX_VALUE);
        options.setMaxAliasesForCollections(1000);
        Yaml parser = new Yaml(new SafeConstructor(options));
        String origin = "";
        String mediaBase = "";
        boolean header = true;
        for (Object document : parser.loadAll(yaml)) {
            if (!(document instanceof Map<?, ?> map)) continue;
            if (header) {
                header = false;
                if ("DEP-11".equals(map.get("File"))) {
                    origin = string(map.get("Origin"));
                    mediaBase = string(map.get("MediaBaseUrl"));
                    continue;
                }
            }
            if (!"desktop-application".equals(map.get("Type"))) continue;
            String pkg = string(map.get("Package"));
            if (pkg.isEmpty()) continue;
            String homepage = map.get("Url") instanceof Map<?, ?> urls ? string(urls.get("homepage")) : "";
            sink.accept(new AppData(string(map.get("ID")), pkg, localized(map.get("Name")),
                    localized(map.get("Summary")), localized(map.get("Description")), strings(map.get("Categories")),
                    icon(map.get("Icon"), origin), homepage, screenshots(map.get("Screenshots"), mediaBase)));
        }
    }

    /** The text for the locale: language and country, the language, or the untranslated one. */
    String localized(Object value) {
        if (!(value instanceof Map<?, ?> texts)) return string(value);
        String language = locale.getLanguage();
        String country = locale.getCountry();
        for (String key : List.of(language + "_" + country, language + "-" + country, language, "C")) {
            Object text = texts.get(key);
            if (text != null) return string(text).strip();
        }
        return "";
    }

    private Optional<Path> icon(Object value, String origin) {
        if (!(value instanceof Map<?, ?> icons) || origin.isEmpty()) return Optional.empty();
        if (!(icons.get("cached") instanceof List<?> cached)) return Optional.empty();
        for (Object o : cached) {
            if (!(o instanceof Map<?, ?> icon) || !"64".equals(string(icon.get("width")))) continue;
            String name = string(icon.get("name"));
            for (Path root : iconRoots) {
                Path file = root.resolve(origin).resolve("64x64").resolve(name);
                if (Files.isRegularFile(file)) return Optional.of(file);
            }
        }
        return Optional.empty();
    }

    private List<AppData.Screenshot> screenshots(Object value, String mediaBase) {
        if (!(value instanceof List<?> list)) return List.of();
        List<AppData.Screenshot> shots = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> shot)) continue;
            String url = bestImage(shot);
            if (url.isEmpty()) continue;
            URI image = resolve(mediaBase, url);
            if (image == null) continue;
            AppData.Screenshot screenshot = new AppData.Screenshot(localized(shot.get("caption")), image);
            if (Boolean.TRUE.equals(shot.get("default"))) {
                shots.addFirst(screenshot);
            } else {
                shots.add(screenshot);
            }
        }
        return shots;
    }

    /** The thumbnail closest to {@link #SCREENSHOT_WIDTH}, or the source image. */
    private static String bestImage(Map<?, ?> shot) {
        String best = "";
        int bestDistance = Integer.MAX_VALUE;
        if (shot.get("thumbnails") instanceof List<?> thumbnails) {
            for (Object o : thumbnails) {
                if (!(o instanceof Map<?, ?> t)) continue;
                int width = number(t.get("width"));
                int distance = Math.abs(width - SCREENSHOT_WIDTH);
                if (width > 0 && distance < bestDistance) {
                    bestDistance = distance;
                    best = string(t.get("url"));
                }
            }
        }
        if (best.isEmpty() && shot.get("source-image") instanceof Map<?, ?> source) best = string(source.get("url"));
        return best;
    }

    /** Only https and http images; relative ones are below the file's MediaBaseUrl. */
    private static URI resolve(String mediaBase, String url) {
        try {
            URI uri = url.contains("://") ? URI.create(url)
                    : URI.create(mediaBase.endsWith("/") ? mediaBase : mediaBase + "/").resolve(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return scheme.equals("https") || scheme.equals("http") ? uri : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(AppStreamReader::string).filter(s -> !s.isEmpty()).toList();
    }

    private static int number(Object value) {
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(string(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }
}
