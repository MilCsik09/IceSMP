package hu.taliann.icesmp.pve;

import java.util.*;

/** Combat-only projection: no level, loot band, Bestiary id, provenance or reward receipt can be replaced. */
public record EffectiveMobProjection(String templateId, MobRank rank, Optional<MobArchetype> archetype,
                                      List<String> abilityIds, MobBehaviorProfile behavior, Set<UUID> projectionIds) {
    public EffectiveMobProjection {
        templateId = Objects.requireNonNullElse(templateId, ""); Objects.requireNonNull(rank); Objects.requireNonNull(archetype); Objects.requireNonNull(behavior);
        if (!templateId.isEmpty()) MobAbilityDefinition.id(templateId, "template");
        abilityIds = CanonicalMobProfile.abilityIds(abilityIds); projectionIds = Set.copyOf(projectionIds);
        if (projectionIds.size() > 32) throw new IllegalArgumentException("Effective profile projection cap");
    }
    public static EffectiveMobProjection canonical(final CanonicalMobProfile profile) {
        return new EffectiveMobProjection(profile.templateId(), profile.rank(), profile.archetype(), profile.rankKits().get(profile.rank()), profile.behavior(), Set.of());
    }
}
