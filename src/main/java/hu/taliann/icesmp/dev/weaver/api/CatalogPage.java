package hu.taliann.icesmp.dev.weaver.api;

import java.util.List;

public record CatalogPage(List<CatalogEntry> entries, int offset, boolean hasNext) {
    public CatalogPage {
        entries = List.copyOf(entries);
        if (entries.size() > 45 || offset < 0) throw new IllegalArgumentException("Invalid catalog page");
        String previous = null;
        for (final CatalogEntry entry : entries) {
            if (previous != null && previous.compareTo(entry.stableId()) >= 0) throw new IllegalArgumentException("Catalog page order or duplicate id");
            previous = entry.stableId();
        }
    }
}
