package hu.taliann.icesmp.pve;

import java.util.*;

public final class MobRuntimeProjectionRegressionSuite {
    private static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
    private static void rejects(final Runnable action) { try { action.run(); throw new AssertionError("Expected rejection"); } catch (final IllegalArgumentException expected) { } }
    private static MobAbilityDefinition ability(final String id, final Set<MobRank> ranks, final Set<MobArchetype> archetypes, final boolean reaction) {
        return new MobAbilityDefinition(id, MobAbilityDefinition.Kind.LUNGE, 40, 10, 0, 4, 2, 0,
                MobAbilityDefinition.TargetRule.CURRENT_TARGET, true, ranks, archetypes, Map.of(),
                Set.of(reaction ? MobAbilityDefinition.Trigger.ON_PROVOKED : MobAbilityDefinition.Trigger.ON_TIMER), List.of(), List.of(), null);
    }
    public static void main(final String[] args) {
        final Map<String, MobAbilityDefinition> definitions = new HashMap<>();
        final List<String> kit = new ArrayList<>();
        for (int i = 0; i < 6; i++) { final String id = "fixture_" + i; definitions.put(id, ability(id, Set.of(), Set.of(), false)); kit.add(id); }
        definitions.put("reaction", ability("reaction", Set.of(), Set.of(), true)); kit.add("reaction");
        definitions.put("elite_only", ability("elite_only", Set.of(MobRank.ELITE), Set.of(), false));
        definitions.put("ranged_only", ability("ranged_only", Set.of(), Set.of(MobArchetype.RANGED), false));
        final Map<MobRank, List<String>> kits = new EnumMap<>(MobRank.class);
        for (final MobRank rank : MobRank.values()) kits.put(rank, kit);
        final var canonical = new CanonicalMobProfile("fixture_template", MobRank.NORMAL, Optional.of(MobArchetype.BRUISER), 12, kits, List.of(), MobBehaviorProfile.defaults(MobArchetype.BRUISER));
        kit.clear(); kits.clear(); check(canonical.rankKits().get(MobRank.NORMAL).size() == 7, "canonical combat inputs retained mutable registry lists");
        final var unchanged = MobRuntimeProjectionSource.canonical().resolve(UUID.randomUUID(), canonical);
        check(unchanged.equals(EffectiveMobProjection.canonical(canonical)) && unchanged.projectionIds().isEmpty(), "default projection changed canonical combat inputs");
        check(MobAbilityRuntime.effectiveDefinitions(unchanged, definitions::get).stream().map(MobAbilityDefinition::abilityId).toList().equals(List.of("fixture_0", "reaction")), "canonical NORMAL kit/counterplay budget changed");
        final int[] expected = {1, 2, 3, 4, 4, 5, 5};
        for (final MobRank rank : MobRank.values()) {
            final var projected = new EffectiveMobProjection("fixture_template", rank, canonical.archetype(), unchanged.abilityIds(), canonical.behavior(), Set.of(UUID.randomUUID()));
            final var selected = MobAbilityRuntime.effectiveDefinitions(projected, definitions::get);
            check(selected.size() == expected[rank.ordinal()] + 1 && selected.getLast().abilityId().equals("reaction"), "effective rank did not control bounded combat kit");
        }
        final var ineligible = new EffectiveMobProjection("fixture_template", MobRank.NORMAL, canonical.archetype(), List.of("elite_only", "ranged_only", "fixture_0"), canonical.behavior(), Set.of(UUID.randomUUID()));
        check(MobAbilityRuntime.effectiveDefinitions(ineligible, definitions::get).stream().map(MobAbilityDefinition::abilityId).toList().equals(List.of("fixture_0")), "projection bypassed canonical ability eligibility");
        final var eligible = new EffectiveMobProjection("fixture_template", MobRank.ELITE, Optional.of(MobArchetype.RANGED), List.of("elite_only", "ranged_only"), MobBehaviorProfile.defaults(MobArchetype.RANGED), Set.of(UUID.randomUUID()));
        check(MobAbilityRuntime.effectiveDefinitions(eligible, definitions::get).size() == 2 && canonical.rank() == MobRank.NORMAL && canonical.level() == 12,
                "effective eligibility changed canonical rank/level or ignored archetype");
        definitions.put("new_content", ability("new_content", Set.of(), Set.of(), false));
        final var dynamic = new EffectiveMobProjection("fixture_template", MobRank.NORMAL, canonical.archetype(), List.of("new_content"), canonical.behavior(), Set.of(UUID.randomUUID()));
        check(MobAbilityRuntime.effectiveDefinitions(dynamic, definitions::get).getFirst().abilityId().equals("new_content"), "new canonical registry content required a combat-runtime code branch");
        rejects(() -> new EffectiveMobProjection("fixture_template", MobRank.NORMAL, canonical.archetype(), List.of("fixture_0", "fixture_0"), canonical.behavior(), Set.of()));
        final Set<UUID> overflow = new HashSet<>(); for (int i = 0; i < 33; i++) overflow.add(UUID.randomUUID());
        rejects(() -> new EffectiveMobProjection("fixture_template", MobRank.NORMAL, canonical.archetype(), List.of(), canonical.behavior(), overflow));
        rejects(() -> new CanonicalMobProfile("fixture_template", MobRank.NORMAL, canonical.archetype(), 12, Map.of(), List.of(), canonical.behavior()));
        System.out.println("Mob runtime projection policy passed: detached canonical inputs, unchanged fallback, all seven rank caps, response counterplay, archetype/rank eligibility and dynamic registry content.");
    }
}
