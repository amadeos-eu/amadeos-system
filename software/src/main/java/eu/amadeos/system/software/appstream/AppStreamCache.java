package eu.amadeos.system.software.appstream;

import eu.amadeos.system.packages.StampedCache;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps what {@link AppStream} and {@link InstalledApps} read in {@code ~/.cache/amadeos-software/appstream},
 * one file per language, until apt fetches new lists, a package is installed or removed, or Software itself
 * is updated. Reading the YAML of all of Debian takes seconds; the file takes a fraction of one.
 */
public final class AppStreamCache implements AppStreamSource {
    /** Names the layout of the file; raise it when AppData or the way it is read changes. */
    static final String FORMAT = "amadeos-appstream 1";

    private final AppStreamSource source;
    private final StampedCache cache;

    AppStreamCache(AppStreamSource source, StampedCache cache) {
        this.source = source;
        this.cache = cache;
    }

    /** The system's AppStream data for {@code locale}, cached in the user's cache directory. */
    public static AppStreamSource inUserCache(AppStreamSource source, Locale locale) {
        List<Path> sources = new ArrayList<>(List.of(AppStream.LISTS, Path.of("/var/lib/dpkg/status"),
                InstalledApps.APPLICATIONS, InstalledApps.METAINFO));
        sources.addAll(AppStream.ICONS);
        StampedCache.codeOf(AppStreamCache.class).ifPresent(sources::add);
        Path file = StampedCache.userCache().resolve("amadeos-software").resolve("appstream-" + locale.toLanguageTag());
        return new AppStreamCache(source, new StampedCache(file, FORMAT + " " + locale.toLanguageTag(), sources));
    }

    @Override
    public Map<String, AppData> applications() {
        String stamp = cache.stamp();
        Optional<Map<String, AppData>> kept = cache.load(stamp, AppStreamCache::decode);
        if (kept.isPresent()) return kept.get();
        Map<String, AppData> read = source.applications();
        cache.save(stamp, read, AppStreamCache::encode);
        return read;
    }

    static void encode(DataOutputStream out, Map<String, AppData> apps) throws IOException {
        out.writeInt(apps.size());
        for (AppData app : apps.values()) {
            out.writeUTF(app.id());
            out.writeUTF(app.pkg());
            out.writeUTF(app.name());
            StampedCache.writeString(out, app.summary());
            StampedCache.writeString(out, app.description());
            out.writeInt(app.categories().size());
            for (String category : app.categories()) out.writeUTF(category);
            out.writeUTF(app.icon().map(Path::toString).orElse(""));
            out.writeUTF(app.homepage());
            out.writeInt(app.screenshots().size());
            for (AppData.Screenshot shot : app.screenshots()) {
                StampedCache.writeString(out, shot.caption());
                out.writeUTF(shot.image().toString());
            }
        }
    }

    static Map<String, AppData> decode(DataInputStream in) throws IOException {
        int count = count(in);
        Map<String, AppData> apps = new HashMap<>();
        for (int i = 0; i < count; i++) {
            String id = in.readUTF();
            String pkg = in.readUTF();
            String name = in.readUTF();
            String summary = StampedCache.readString(in);
            String description = StampedCache.readString(in);
            int categoryCount = count(in);
            List<String> categories = new ArrayList<>(categoryCount);
            for (int c = 0; c < categoryCount; c++) categories.add(in.readUTF());
            String icon = in.readUTF();
            String homepage = in.readUTF();
            int shotCount = count(in);
            List<AppData.Screenshot> shots = new ArrayList<>(shotCount);
            for (int s = 0; s < shotCount; s++) {
                String caption = StampedCache.readString(in);
                shots.add(new AppData.Screenshot(caption, URI.create(in.readUTF())));
            }
            apps.put(pkg, new AppData(id, pkg, name, summary, description, categories,
                    icon.isEmpty() ? Optional.empty() : Optional.of(Path.of(icon)), homepage, shots));
        }
        return apps;
    }

    private static int count(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > 1 << 20) throw new IOException("bad count " + count);
        return count;
    }
}
