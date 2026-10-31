package eu.amadeos.system.users;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Login and group names as Debian accepts them by default (adduser's NAME_REGEX): a small letter first, then
 * small letters, digits, '-' and '_', at most 32 characters. Suggests one from a full name.
 */
public final class LoginNames {
    public static final int MAX_LENGTH = 32;
    private static final Pattern VALID = Pattern.compile("[a-z][-a-z0-9_]{0,31}");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    private LoginNames() {
    }

    public static boolean valid(String name) {
        return name != null && VALID.matcher(name).matches();
    }

    /**
     * "Anna Müller" gives the first name in small letters ("anna"); when that is
     * taken, first name and the initial of the last ("annam"), then a number ("anna2").
     */
    public static String suggest(String fullName, Collection<String> taken) {
        String[] words = ascii(fullName).split("[^a-z0-9]+");
        String first = "";
        String lastInitial = "";
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (first.isEmpty()) first = w;
            else lastInitial = w.substring(0, 1);
        }
        while (!first.isEmpty() && !Character.isLetter(first.charAt(0))) first = first.substring(1);
        if (first.isEmpty()) return "";
        if (first.length() > MAX_LENGTH - 3) first = first.substring(0, MAX_LENGTH - 3);
        if (!taken.contains(first)) return first;
        if (!lastInitial.isEmpty() && !taken.contains(first + lastInitial)) return first + lastInitial;
        for (int i = 2; ; i++) {
            if (!taken.contains(first + i)) return first + i;
        }
    }

    /** Small letters without accents: "Jürgen Weiß" gives "juergen weiss". */
    static String ascii(String text) {
        String s = text.toLowerCase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
                .replace("æ", "ae").replace("ø", "oe").replace("å", "aa").replace("œ", "oe").replace("ł", "l");
        return MARKS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
    }

    /** True if a full name can go into /etc/passwd: no ':' ',' '=' or line break, at most 64 characters. */
    public static boolean validFullName(String name) {
        return name != null && name.length() <= 64 && name.chars().noneMatch(c -> c == ':' || c == ',' || c == '='
                || c == '\n' || c == '\r' || Character.isISOControl(c));
    }
}
