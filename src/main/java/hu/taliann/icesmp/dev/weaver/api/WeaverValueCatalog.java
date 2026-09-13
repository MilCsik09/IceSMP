package hu.taliann.icesmp.dev.weaver.api;

import java.util.Optional;

public interface WeaverValueCatalog {
    WeaverTypeId type();
    CatalogPage page(CatalogQuery query);
    Optional<WeaverValue> resolve(String stableId);
}
