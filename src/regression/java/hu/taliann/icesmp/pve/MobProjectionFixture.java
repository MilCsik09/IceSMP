package hu.taliann.icesmp.pve;

import java.util.*;

/** Test bridge to the actual native combat selection policy, without reflection or production access widening. */
public final class MobProjectionFixture {
    private MobProjectionFixture() { }
    public static List<String> selected(final EffectiveMobProjection profile, final Map<String, MobAbilityDefinition> abilities) {
        return MobAbilityRuntime.effectiveDefinitions(profile, abilities::get).stream().map(MobAbilityDefinition::abilityId).toList();
    }
}
