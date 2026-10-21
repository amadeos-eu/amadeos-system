package eu.amadeos.system.software.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.packages.PackageInfo;
import eu.amadeos.system.software.appstream.AppData;

import java.awt.Color;
import java.awt.Font;
import java.nio.file.Path;
import java.util.Optional;

/** The details pane's page: what a package is, its state, a screenshot and the description, as XHTML. */
final class DetailsPage {
    /** Where the screenshot of an application stands. */
    sealed interface Shot {
        record None() implements Shot {
        }

        record Loading() implements Shot {
        }

        record Failed() implements Shot {
        }

        /** The cached file, drawn {@code width} by {@code height} pixels. */
        record Shown(Path file, int width, int height, String caption) implements Shot {
        }
    }

    static final Shot NO_SHOT = new Shot.None();

    private DetailsPage() {
    }

    static String build(Messages msg, Item item, Optional<PackageInfo> info, String state, Shot shot, Font font,
                        Color text, Color link) {
        StringBuilder body = new StringBuilder();
        body.append("<table class='head'><tr>");
        item.app().flatMap(AppData::icon).ifPresent(icon -> body.append("<td class='icon'><img src='")
                .append(Html.escape(icon.toUri().toString())).append("' width='48' height='48' alt=''/></td>"));
        body.append("<td><h1>").append(Html.escape(item.title())).append("</h1>");
        if (!item.title().equals(item.name())) {
            body.append("<div class='package'>").append(Html.escape(msg.format("details.package", item.name())))
                    .append("</div>");
        }
        body.append("</td></tr></table>");
        if (!item.summary().isBlank()) body.append("<p class='summary'>").append(Html.escape(item.summary())).append("</p>");
        body.append("<p class='state'>").append(Html.escape(state)).append("</p>");

        String homepage = item.app().map(AppData::homepage).filter(h -> !h.isBlank())
                .or(() -> info.map(PackageInfo::homepage).filter(h -> !h.isBlank())).orElse("");
        StringBuilder meta = new StringBuilder();
        info.ifPresent(i -> meta.append(Html.escape(msg.format("details.size", Sizes.bytes(i.downloadSize()),
                Sizes.kib(i.installedSize())))));
        if (homepage.startsWith("https://") || homepage.startsWith("http://")) {
            if (!meta.isEmpty()) meta.append(" · ");
            meta.append("<a href='").append(Html.escape(homepage)).append("'>")
                    .append(Html.escape(msg.get("details.homepage"))).append("</a>");
        }
        if (!meta.isEmpty()) body.append("<p class='meta'>").append(meta).append("</p>");

        switch (shot) {
            case Shot.Loading ignored -> body.append("<p class='shot-note'>")
                    .append(Html.escape(msg.get("details.screenshot.loading"))).append("</p>");
            case Shot.Failed ignored -> body.append("<p class='shot-note'>")
                    .append(Html.escape(msg.get("details.screenshot.failed"))).append("</p>");
            case Shot.Shown s -> {
                body.append("<div class='shot'><img src='").append(Html.escape(s.file().toUri().toString()))
                        .append("' width='").append(s.width()).append("' height='").append(s.height())
                        .append("' alt=''/>");
                if (!s.caption().isBlank()) {
                    body.append("<div class='caption'>").append(Html.escape(s.caption())).append("</div>");
                }
                body.append("</div>");
            }
            case Shot.None ignored -> {
            }
        }

        String description = item.app().map(AppData::description).filter(d -> !d.isBlank()).map(Html::fromAppStream)
                .orElseGet(() -> info.map(PackageInfo::description).filter(d -> !d.isBlank()).map(Html::fromDebian)
                        .orElse(""));
        body.append("<div class='description'>").append(description).append("</div>");

        return "<html xmlns='http://www.w3.org/1999/xhtml'><head><style type='text/css'>" + css(font, text, link)
                + "</style></head><body>" + body + "</body></html>";
    }

    /** Looks like the rest of the window: its font, its colours. */
    private static String css(Font font, Color text, Color link) {
        String family = Html.escape(font.getFamily()).replace("'", "");
        return "body { margin: 6px 10px; font-family: '" + family + "', sans-serif; font-size: " + font.getSize()
                + "px; color: " + hex(text) + "; }"
                + " table.head { border-collapse: collapse; margin: 0; }"
                + " td { padding: 0; vertical-align: middle; } td.icon { padding-right: 10px; }"
                + " h1 { font-size: 140%; margin: 0; }"
                + " .package { font-size: 90%; }"
                + " p { margin: 0.4em 0; } .summary { font-weight: bold; }"
                + " a { color: " + hex(link) + "; }"
                + " .shot { margin: 0.8em 0; } .caption { font-size: 90%; font-style: italic; }"
                + " .shot-note { font-style: italic; }"
                + " ul, ol { margin: 0.4em 0; padding-left: 1.5em; } li { margin: 0.15em 0; }"
                + " pre { font-size: 95%; white-space: pre-wrap; }";
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
