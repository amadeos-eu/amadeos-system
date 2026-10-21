package eu.amadeos.system.software.ui;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Descriptions as XHTML for the details pane. Debian's long descriptions are plain text with conventions
 * (paragraphs, indented lines kept as they are, "* " and "- " items); AppStream's are a small markup that is
 * passed on with only its own elements kept.
 */
final class Html {
    /** The elements AppStream allows in a description. */
    private static final Set<String> APPSTREAM_ELEMENTS = Set.of("p", "ul", "ol", "li", "em", "code");

    private Html() {
    }

    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (char c : text.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> {
                    // XML 1.0 has no place for most control characters.
                    if (c >= 0x20 || c == '\n' || c == '\t') out.append(c);
                }
            }
        }
        return out.toString();
    }

    /**
     * A Debian long description (paragraphs separated by an empty line, as {@code PackageInfo} has it) as
     * XHTML: lines indented by two spaces or more stay as they are, "* ", "- " and "o " start list items.
     */
    static String fromDebian(String text) {
        StringBuilder html = new StringBuilder();
        for (String paragraph : text.strip().split("\n\\s*\n")) {
            List<String> lines = paragraph.lines().toList();
            if (lines.isEmpty()) continue;
            if (lines.stream().allMatch(l -> l.startsWith(" "))) {
                if (lines.stream().allMatch(Html::isItem)) {
                    list(html, lines);
                } else {
                    html.append("<pre>").append(escape(String.join("\n", lines))).append("</pre>");
                }
                continue;
            }
            // Text, perhaps followed by items: "Features:\n * one\n * two".
            int firstItem = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (isItem(lines.get(i))) {
                    firstItem = i;
                    break;
                }
            }
            List<String> text1 = firstItem < 0 ? lines : lines.subList(0, firstItem);
            if (!text1.isEmpty()) {
                html.append("<p>").append(escape(String.join(" ", text1.stream().map(String::strip).toList())))
                        .append("</p>");
            }
            if (firstItem >= 0) list(html, lines.subList(firstItem, lines.size()));
        }
        return html.toString();
    }

    private static boolean isItem(String line) {
        String s = line.stripLeading();
        return s.startsWith("* ") || s.startsWith("- ") || s.startsWith("o ") || s.startsWith("+ ");
    }

    /** Items; a line that does not start one continues the item before. */
    private static void list(StringBuilder html, List<String> lines) {
        List<String> items = new ArrayList<>();
        for (String line : lines) {
            if (isItem(line) || items.isEmpty()) {
                String s = line.stripLeading();
                items.add(isItem(line) ? s.substring(2).strip() : s.strip());
            } else {
                items.set(items.size() - 1, items.getLast() + " " + line.strip());
            }
        }
        html.append("<ul>");
        for (String item : items) html.append("<li>").append(escape(item)).append("</li>");
        html.append("</ul>");
    }

    /**
     * An AppStream description with only its own elements kept (others give their text), as well-formed XHTML;
     * text that is not markup at all becomes one escaped paragraph.
     */
    static String fromAppStream(String markup) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            Document doc = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader("<div>" + markup + "</div>")));
            StringBuilder html = new StringBuilder();
            children(doc.getDocumentElement(), html);
            return html.toString();
        } catch (Exception e) {
            return "<p>" + escape(markup) + "</p>";
        }
    }

    private static void children(Node parent, StringBuilder html) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.CDATA_SECTION_NODE) {
                html.append(escape(n.getNodeValue()));
            } else if (n instanceof Element e) {
                String name = e.getTagName();
                boolean kept = APPSTREAM_ELEMENTS.contains(name);
                if (kept) html.append('<').append(name).append('>');
                children(e, html);
                if (kept) html.append("</").append(name).append('>');
            }
        }
    }
}
