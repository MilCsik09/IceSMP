package hu.taliann.icesmp.pve;

import java.util.*;

/** Detached combat inputs; reward identity remains owned by scaling/species/encounter authorities. */
public record CanonicalMobProfile(String templateId, MobRank rank, Optional<MobArchetype> archetype, int level,
                                  Map<MobRank, List<String>> rankKits, List<EliteAffix> affixes, MobBehaviorProfile behavior) {
    public CanonicalMobProfile {
        templateId = Objects.requireNonNullElse(templateId, ""); Objects.requireNonNull(rank); Objects.requireNonNull(archetype); Objects.requireNonNull(behavior);
        if (!templateId.isEmpty()) MobAbilityDefinition.id(templateId, "template");
        if (level < 0) throw new IllegalArgumentException("Negative canonical level");
        final Map<MobRank, List<String>> frozen = new EnumMap<>(MobRank.class);
        rankKits.forEach((key, ids) -> frozen.put(key, abilityIds(ids))); rankKits = Map.copyOf(frozen);
        if (!rankKits.keySet().equals(EnumSet.allOf(MobRank.class))) throw new IllegalArgumentException("Incomplete canonical rank kits");
        affixes = List.copyOf(affixes); EliteAffix.validate(affixes);
    }
    static List<String> abilityIds(final List<String> values) {
        final List<String> result = List.copyOf(values);
        if (result.size() > 128 || result.stream().distinct().count() != result.size()) throw new IllegalArgumentException("Combat kit bounds or duplicates");
        result.forEach(value -> MobAbilityDefinition.id(value, "ability")); return result;
    }
}
