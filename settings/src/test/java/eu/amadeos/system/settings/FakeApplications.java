package eu.amadeos.system.settings;

import eu.amadeos.system.base.xdg.XdgDesktopEntry;

import java.util.Arrays;
import java.util.List;

/** The programs of the amadeOS image with the types they open, for tests and pictures of Default Applications. */
final class FakeApplications {
    static final List<XdgDesktopEntry> ALL = List.of(
            app("firefox-esr.desktop", "Firefox ESR", "x-scheme-handler/http;x-scheme-handler/https;text/html;application/xhtml+xml"),
            app("amade-web.desktop", "Web", "text/html;application/xhtml+xml"),
            app("amade-mail.desktop", "Mail", "x-scheme-handler/mailto"),
            app("amade-files.desktop", "Files", "inode/directory"),
            app("amade-pad.desktop", "Text Editor", "text/plain;text/x-java;text/x-c;text/x-shellscript"),
            app("amade-writepad.desktop", "WritePad", "application/vnd.oasis.opendocument.text;application/vnd.openxmlformats-officedocument.wordprocessingml.document;application/msword;application/rtf;text/rtf;text/plain"),
            app("libreoffice-writer.desktop", "LibreOffice Writer", "application/vnd.oasis.opendocument.text;application/vnd.openxmlformats-officedocument.wordprocessingml.document;application/msword;application/rtf;text/rtf"),
            app("amade-imageviewer.desktop", "Image Viewer", "image/jpeg;image/png;image/gif;image/bmp;image/x-bmp;image/tiff;image/webp"),
            app("amade-imageeditor.desktop", "Image Editor", "image/jpeg;image/png;image/gif;image/bmp;image/tiff;image/webp"),
            app("amade-imagescan.desktop", "Imagescan", "image/png;image/jpeg;image/tiff"),
            app("amade-pdfviewer.desktop", "PDF Viewer", "application/pdf"),
            app("amade-books.desktop", "Books", "application/epub+zip;application/vnd.comicbook+zip;application/x-cbz"),
            app("amade-audio.desktop", "Audio", "audio/mpeg;audio/ogg;audio/x-vorbis+ogg;audio/flac;audio/x-flac;audio/wav;audio/x-wav;audio/aac;audio/x-mpegurl"),
            app("amade-video.desktop", "Video", "video/mp4;video/x-m4v;video/x-matroska;video/webm;video/x-msvideo;video/quicktime;video/mpeg;video/ogg"),
            app("amade-calendar.desktop", "Calendar", "text/calendar"),
            app("amade-contacts.desktop", "Contacts", "text/vcard;text/x-vcard;text/directory"));

    private FakeApplications() {
    }

    private static XdgDesktopEntry app(String id, String name, String types) {
        return new XdgDesktopEntry(id, name, "", "", "", "/usr/bin/" + id.replace(".desktop", ""), false, List.of(),
                Arrays.asList(types.split(";")), false, false, List.of(), List.of(), "", null);
    }
}
