package eu.amadeos.system.software.ui;

import eu.amadeos.system.software.appstream.AppData;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The groups of the Applications tab, from the freedesktop menu categories of the AppStream data, or from the
 * Debian section when a package has none. An application can be in several groups ("Game;Education").
 */
enum Group {
    AUDIO_VIDEO("group.audio-video"),
    DEVELOPMENT("group.development"),
    EDUCATION("group.education"),
    GAMES("group.games"),
    GRAPHICS("group.graphics"),
    INTERNET("group.internet"),
    OFFICE("group.office"),
    SCIENCE("group.science"),
    SYSTEM("group.system"),
    UTILITIES("group.utilities"),
    /** Applications that name no main category. */
    OTHER("group.other");

    /** The freedesktop main categories (and the additional ones that clearly belong somewhere). */
    private static final Map<String, Group> CATEGORIES = Map.ofEntries(
            Map.entry("AudioVideo", AUDIO_VIDEO), Map.entry("Audio", AUDIO_VIDEO), Map.entry("Video", AUDIO_VIDEO),
            Map.entry("Development", DEVELOPMENT), Map.entry("Education", EDUCATION), Map.entry("Game", GAMES),
            Map.entry("Graphics", GRAPHICS), Map.entry("Network", INTERNET), Map.entry("Office", OFFICE),
            Map.entry("Science", SCIENCE), Map.entry("Math", SCIENCE), Map.entry("Settings", SYSTEM),
            Map.entry("System", SYSTEM), Map.entry("Utility", UTILITIES));
    private static final Map<String, Group> SECTIONS = Map.ofEntries(
            Map.entry("sound", AUDIO_VIDEO), Map.entry("video", AUDIO_VIDEO), Map.entry("devel", DEVELOPMENT),
            Map.entry("vcs", DEVELOPMENT), Map.entry("interpreters", DEVELOPMENT), Map.entry("education", EDUCATION),
            Map.entry("games", GAMES), Map.entry("graphics", GRAPHICS), Map.entry("web", INTERNET),
            Map.entry("net", INTERNET), Map.entry("mail", INTERNET), Map.entry("news", INTERNET),
            Map.entry("comm", INTERNET), Map.entry("editors", OFFICE), Map.entry("text", OFFICE),
            Map.entry("science", SCIENCE), Map.entry("math", SCIENCE), Map.entry("electronics", SCIENCE),
            Map.entry("hamradio", SCIENCE), Map.entry("admin", SYSTEM), Map.entry("utils", UTILITIES));

    final String key;

    Group(String key) {
        this.key = key;
    }

    static Set<Group> of(Item item) {
        List<String> categories = item.app().map(AppData::categories).orElse(List.of());
        Set<Group> groups = EnumSet.noneOf(Group.class);
        for (String category : categories) {
            Group group = CATEGORIES.get(category);
            if (group != null) groups.add(group);
        }
        if (groups.isEmpty()) {
            Group group = SECTIONS.get(item.entry().section());
            groups.add(group != null ? group : OTHER);
        }
        return groups;
    }
}
