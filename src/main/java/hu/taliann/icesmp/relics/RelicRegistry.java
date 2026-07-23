package hu.taliann.icesmp.relics;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reload (clear+register sorozat) a reload-szálon fut, miközben régió-szálak olvasnak —
 * a metódusok synchronized-oltak, hogy a rebuild alatt se lásson senki fél-kész map-et.
 * A LinkedHashMap a regisztrációs sorrendet őrzi (listázások stabil sorrendje).
 */
public final class RelicRegistry {

    private final Map<String, RelicDefinition> definitions = new LinkedHashMap<>();

    public synchronized void clear() {
        definitions.clear();
    }

    public synchronized void register(final RelicDefinition definition) {
        if (definition == null || definition.id() == null || definition.id().isBlank()) {
            return;
        }

        definitions.put(normalize(definition.id()), definition);
    }

    public synchronized RelicDefinition findById(final String relicId) {
        if (relicId == null || relicId.isBlank()) {
            return null;
        }

        return definitions.get(normalize(relicId));
    }

    public synchronized Collection<RelicDefinition> all() {
        return Collections.unmodifiableCollection(new java.util.ArrayList<>(definitions.values()));
    }

    private String normalize(final String relicId) {
        return relicId.trim().toLowerCase(Locale.ROOT);
    }
}
