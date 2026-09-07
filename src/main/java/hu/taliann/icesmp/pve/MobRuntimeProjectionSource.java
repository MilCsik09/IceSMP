package hu.taliann.icesmp.pve;

import java.util.UUID;

/** Runtime reads immutable effective inputs; this port cannot mutate canonical registries or reward identity. */
@FunctionalInterface
public interface MobRuntimeProjectionSource {
    EffectiveMobProjection resolve(UUID entityId, CanonicalMobProfile canonical);
    static MobRuntimeProjectionSource canonical() { return (entity, profile) -> EffectiveMobProjection.canonical(profile); }
}
