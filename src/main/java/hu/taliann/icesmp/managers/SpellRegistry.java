package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.spells.Spell;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Startup-owned, fail-fast registry of unique spell ids. */
public final class SpellRegistry {

    private final Map<String, Spell> spellsById = new LinkedHashMap<>();

    public void register(final Spell spell) {
        if (spell == null) {
            throw new IllegalArgumentException("Cannot register a null spell");
        }
        final String normalized = normalizeRequiredId(spell.getId());
        final Spell previous = spellsById.putIfAbsent(normalized, spell);
        if (previous != null) {
            throw new IllegalStateException("Duplicate spell id '" + normalized + "': "
                    + previous.getClass().getName() + " is already registered; rejected "
                    + spell.getClass().getName());
        }
    }

    public Spell getById(final String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return spellsById.get(id.trim().toLowerCase(Locale.ROOT));
    }

    public Collection<Spell> getAll() {
        return Collections.unmodifiableCollection(spellsById.values());
    }

    private static String normalizeRequiredId(final String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A registered spell must have a non-blank id");
        }
        return id.trim().toLowerCase(Locale.ROOT);
    }
}
