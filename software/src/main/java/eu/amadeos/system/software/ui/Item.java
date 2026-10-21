package eu.amadeos.system.software.ui;

import eu.amadeos.system.packages.CatalogEntry;
import eu.amadeos.system.software.appstream.AppData;

import java.util.Optional;

/** One row of a catalog tab: the package, and its application's AppStream data when there is some. */
record Item(CatalogEntry entry, Optional<AppData> app) {

    static Item of(CatalogEntry entry) {
        return new Item(entry, Optional.empty());
    }

    /** The application's name, or the package name. */
    String title() {
        return app.map(AppData::name).filter(n -> !n.isBlank()).orElse(entry.name());
    }

    /** The application's summary (translated), or the package's. */
    String summary() {
        return app.map(AppData::summary).filter(s -> !s.isBlank()).orElse(entry.summary());
    }

    String name() {
        return entry.name();
    }
}
