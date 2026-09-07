package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import java.util.UUID;

public final class WeaverProviderTestContext {
    private WeaverProviderTestContext() { }
    public static ProviderContext sandbox(final WeaverTypeRegistry types) {
        return new ProviderContext(new WeaverAuthorityToken(HiddenDevAuthority.PRIMARY_DEVELOPER, UUID.randomUUID(), Long.MAX_VALUE, () -> true, () -> 0L), types, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
    }
}
