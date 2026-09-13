package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.integrity.*;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.*;

/** Native potion absence is observed on its player owner, never inferred from nominal tick duration. */
final class PvEInfluenceLifetimeObserver implements WeaverInfluenceObserverProvider {
    static final WeaverTypeId TYPE = WeaverTypeId.parse("icesmp:pve_potion_effect@1");
    private final WeaverProviderServices services;
    PvEInfluenceLifetimeObserver(final WeaverProviderServices services) {
        this.services = services;
        services.types().register(new WeaverTypeCodec() {
            public WeaverTypeId type() { return TYPE; }
            public ValidationResult validate(Map<String, Object> payload) {
                if (!payload.keySet().equals(Set.of("potion")) || !(payload.get("potion") instanceof String text)) return ValidationResult.rejected("POTION_REFERENCE_REQUIRED");
                final NamespacedKey key = NamespacedKey.fromString(text);
                return key != null && key.toString().equals(text) && Registry.EFFECT.get(key) != null
                        ? ValidationResult.accepted() : ValidationResult.rejected("POTION_REFERENCE_UNAVAILABLE");
            }
            public byte[] canonicalBytes(Map<String, Object> payload) { validate(payload).requireValid(); return CanonicalValueBytes.encode(payload); }
        });
    }
    @Override public List<InfluenceLifetimeDescriptor> influenceLifetimes() {
        return List.of(new InfluenceLifetimeDescriptor("pve", PvEWeaverProvider.FACET, TYPE, Set.of(InfluenceScope.PLAYER)));
    }
    @Override public CompletionStage<InfluenceObservation> observeInfluence(final WeaverInfluenceRecord influence) {
        if (!(influence.target().source() instanceof RewardSource.Player target)) return CompletableFuture.completedFuture(InfluenceObservation.unavailable());
        final String potion = (String) influence.observedLifetime().orElseThrow().payload().get("potion");
        return WeaverInfluenceOwnerObservation.submit(services.owners(), target.id(), () -> observeOwned(target, potion));
    }
    private InfluenceObservation observeOwned(final RewardSource.Player target, final String potion) {
        final var entity = Bukkit.getEntity(target.id());
        if (entity == null || !Bukkit.isOwnedByCurrentRegion(entity) || !(entity instanceof Player player) || !player.isOnline())
            return InfluenceObservation.unavailable();
        return services.readConsumer("pve", () -> {
            final var effect = Registry.EFFECT.get(Objects.requireNonNull(NamespacedKey.fromString(potion)));
            if (effect == null) return InfluenceObservation.unavailable();
            final var fence = GameplayEffectGate.fence(target).orElse(null);
            if (fence == null) return InfluenceObservation.unavailable();
            boolean retain = false;
            try {
                if (player.hasPotionEffect(effect)) return InfluenceObservation.active();
                retain = true; return InfluenceObservation.ended(fence);
            } finally { if (!retain) fence.close(); }
        });
    }
}
