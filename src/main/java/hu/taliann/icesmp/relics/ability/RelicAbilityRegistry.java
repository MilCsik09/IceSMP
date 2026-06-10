package hu.taliann.icesmp.relics.ability;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class RelicAbilityRegistry {

    private final Map<String, RelicAbility> abilities = new HashMap<>();

    public void register(final RelicAbility ability) {
        if (ability == null || ability.id() == null || ability.id().isBlank()) {
            return;
        }

        abilities.put(normalize(ability.id()), ability);
    }

    public RelicAbility find(final String abilityId) {
        if (abilityId == null || abilityId.isBlank()) {
            return null;
        }

        return abilities.get(normalize(abilityId));
    }

    private String normalize(final String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}

