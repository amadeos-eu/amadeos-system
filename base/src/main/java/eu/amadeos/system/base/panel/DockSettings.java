package eu.amadeos.system.base.panel;

import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.system.base.xdg.XdgDirectories;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * The settings of Horizon's dock in {@code ~/.config/amadeos/dock.conf}: the favourite programs (desktop entry
 * ids, in their order), the icon size and whether the program drawers and the folder drawers are shown. The
 * dock writes the favourites (Keep in dock, Remove from dock); Settings > Desktop look writes the rest. The dock
 * watches the file and follows a change at once.
 *
 * @param favourites desktop entry ids such as {@code amade-files.desktop}
 * @param iconSize   one of {@link #SIZES}
 */
public record DockSettings(List<String> favourites, int iconSize, boolean drawers, boolean folders) {
    public static final String FILE_NAME = "dock.conf";
    /** Small, medium and large icons. */
    public static final int[] SIZES = {36, 44, 52};
    /** The first favourites, those of them that are installed. */
    public static final List<String> DEFAULT_FAVOURITES = List.of("amade-files.desktop", "firefox-esr.desktop",
            "amade-mail.desktop", "amade-calendar.desktop", "amade-writepad.desktop", "amade-audio.desktop",
            "amade-terminal.desktop", "amadeos-software.desktop");
    public static final DockSettings DEFAULTS = new DockSettings(DEFAULT_FAVOURITES, 44, true, true);

    public DockSettings {
        favourites = List.copyOf(favourites);
        iconSize = nearestSize(iconSize);
    }

    public DockSettings withFavourites(List<String> list) {
        return new DockSettings(list, iconSize, drawers, folders);
    }

    /** Adds {@code id} at the end, or removes it. */
    public DockSettings withFavourite(String id, boolean keep) {
        List<String> list = new ArrayList<>(favourites);
        list.remove(id);
        if (keep) list.add(id);
        return withFavourites(list);
    }

    static int nearestSize(int size) {
        int best = SIZES[0];
        for (int s : SIZES) {
            if (Math.abs(s - size) < Math.abs(best - size)) best = s;
        }
        return best;
    }

    public static Path userFile() {
        return XdgDirectories.amadeosConfigDir().resolve(FILE_NAME);
    }

    public static DockSettings load(Path file) {
        Properties p;
        try {
            p = PropertiesFile.load(file);
        } catch (RuntimeException e) {
            p = new Properties();
        }
        DockSettings d = DEFAULTS;
        String fav = p.getProperty("favourites");
        List<String> favourites = fav == null ? d.favourites()
                : Arrays.stream(fav.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
        int size;
        try {
            size = Integer.parseInt(p.getProperty("icon.size", Integer.toString(d.iconSize())).strip());
        } catch (NumberFormatException e) {
            size = d.iconSize();
        }
        return new DockSettings(favourites, size,
                Boolean.parseBoolean(p.getProperty("drawers", Boolean.toString(d.drawers())).strip()),
                Boolean.parseBoolean(p.getProperty("folders", Boolean.toString(d.folders())).strip()));
    }

    public void save(Path file) {
        Properties p = new Properties();
        p.setProperty("favourites", String.join(",", favourites));
        p.setProperty("icon.size", Integer.toString(iconSize));
        p.setProperty("drawers", Boolean.toString(drawers));
        p.setProperty("folders", Boolean.toString(folders));
        PropertiesFile.store(file, p, "amadeOS Horizon dock (Settings > Desktop look)");
    }
}
