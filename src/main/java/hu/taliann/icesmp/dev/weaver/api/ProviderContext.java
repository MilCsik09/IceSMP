package hu.taliann.icesmp.dev.weaver.api;

import hu.taliann.icesmp.dev.weaver.WeaverAuthorityToken;

public record ProviderContext(WeaverAuthorityToken authority, WeaverTypeRegistry types, Lifetime lifetime, IntegrityMode integrityMode) {
    public ProviderContext {
        java.util.Objects.requireNonNull(authority); java.util.Objects.requireNonNull(types);
        java.util.Objects.requireNonNull(lifetime); java.util.Objects.requireNonNull(integrityMode);
        authority.requireValid();
    }
}
