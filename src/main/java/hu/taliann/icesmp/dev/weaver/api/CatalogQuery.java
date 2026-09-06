package hu.taliann.icesmp.dev.weaver.api;

public record CatalogQuery(String search, int offset, int limit) {
    public CatalogQuery {
        if (search == null || search.length() > 128 || offset < 0 || offset > 1_000_000 || limit < 1 || limit > 45) {
            throw new IllegalArgumentException("Catalog query exceeds bounds");
        }
        search = search.toLowerCase(java.util.Locale.ROOT);
    }
}
