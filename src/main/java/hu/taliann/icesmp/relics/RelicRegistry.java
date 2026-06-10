package hu.taliann.icesmp.relics;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class RelicRegistry {

    private final Map<String, RelicDefinition> definitions = new LinkedHashMap<>();

    public void clear() {
        definitions.clear();
    }

    public void register(final RelicDefinition definition) {
        if (definition == null || definition.id() == null || definition.id().isBlank()) {
            return;
        }

        definitions.put(normalize(definition.id()), definition);
    }

    public RelicDefinition findById(final String relicId) {
        if (relicId == null || relicId.isBlank()) {
            return null;
        }

        return definitions.get(normalize(relicId));
    }

    public Collection<RelicDefinition> all() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    private String normalize(final String relicId) {
        return relicId.trim().toLowerCase(Locale.ROOT);
    }
}

