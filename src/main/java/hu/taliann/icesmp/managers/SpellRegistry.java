package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.spells.Spell;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SpellRegistry {

    private final Map<String, Spell> spellsById = new LinkedHashMap<>();

    public void register(final Spell spell) {
        if (spell == null || spell.getId() == null || spell.getId().isBlank()) {
            return;
        }

        spellsById.put(spell.getId().trim().toLowerCase(), spell);
    }

    public Spell getById(final String id) {
        if (id == null || id.isBlank()) {
            return null;
        }

        return spellsById.get(id.trim().toLowerCase());
    }

    public Collection<Spell> getAll() {
        return Collections.unmodifiableCollection(spellsById.values());
    }
}

