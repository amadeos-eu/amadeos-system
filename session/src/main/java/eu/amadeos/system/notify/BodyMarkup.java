package eu.amadeos.system.notify;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The body of a notification as HTML for a Swing label. The specification allows {@code <b>}, {@code <i>},
 * {@code <u>}, {@code <a href>} and {@code <img>}; everything else is shown as text. Links keep only http, https
 * and mailto targets; images are left out. Plain text bodies (most programs) are escaped and their line breaks
 * kept.
 */
final class BodyMarkup {
    private static final Pattern TAG = Pattern.compile("<(/?)([a-zA-Z]+)([^>]*)>");
    private static final Pattern HREF = Pattern.compile("href\\s*=\\s*(\"([^\"]*)\"|'([^']*)')", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENTITY = Pattern.compile("&(amp|lt|gt|quot|apos|#\\d{1,6}|#x[0-9a-fA-F]{1,6});");

    private BodyMarkup() {
    }

    /** The body as HTML without the {@code <html>} wrapper. */
    static String toHtml(String body) {
        if (body == null || body.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        Matcher m = TAG.matcher(body);
        int at = 0;
        while (m.find()) {
            text(body.substring(at, m.start()), out);
            String name = m.group(2).toLowerCase(Locale.ROOT);
            boolean closing = !m.group(1).isEmpty();
            switch (name) {
                case "b", "i", "u" -> out.append('<').append(closing ? "/" : "").append(name).append('>');
                case "br" -> out.append("<br>");
                case "a" -> {
                    if (closing) {
                        out.append("</a>");
                    } else {
                        String href = href(m.group(3));
                        out.append(href == null ? "<a>" : "<a href=\"" + escape(href) + "\">");
                    }
                }
                case "img" -> { }
                default -> text(m.group(), out);
            }
            at = m.end();
        }
        text(body.substring(at), out);
        return out.toString();
    }

    /** The body without markup, for the history's one line and the tooltip. */
    static String plain(String body) {
        if (body == null) return "";
        String noTags = TAG.matcher(body).replaceAll(r -> r.group(2).equalsIgnoreCase("br") ? "\n" : "");
        return unescape(noTags);
    }

    private static String href(String attributes) {
        Matcher h = HREF.matcher(attributes);
        if (!h.find()) return null;
        String url = unescape(h.group(2) != null ? h.group(2) : h.group(3)).strip();
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("mailto:") ? url : null;
    }

    /** Text between tags: entities the sender wrote stay, everything else is escaped. */
    private static void text(String s, StringBuilder out) {
        int at = 0;
        Matcher e = ENTITY.matcher(s);
        while (e.find()) {
            out.append(escape(s.substring(at, e.start())));
            out.append(e.group());
            at = e.end();
        }
        out.append(escape(s.substring(at)));
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("\n", "<br>");
    }

    private static String unescape(String s) {
        Matcher e = ENTITY.matcher(s);
        StringBuilder out = new StringBuilder();
        while (e.find()) {
            String g = e.group(1);
            String r = switch (g) {
                case "amp" -> "&";
                case "lt" -> "<";
                case "gt" -> ">";
                case "quot" -> "\"";
                case "apos" -> "'";
                default -> {
                    try {
                        int cp = g.startsWith("#x") ? Integer.parseInt(g.substring(2), 16) : Integer.parseInt(g.substring(1));
                        yield Character.isValidCodePoint(cp) ? new String(Character.toChars(cp)) : "";
                    } catch (NumberFormatException ex) {
                        yield "";
                    }
                }
            };
            e.appendReplacement(out, Matcher.quoteReplacement(r));
        }
        e.appendTail(out);
        return out.toString();
    }
}
