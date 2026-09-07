package hu.taliann.icesmp.factions;

import java.util.*;
import hu.taliann.icesmp.factions.FactionPassivePolicy.ContentContext;

/** Runtime semantic interpretation only; canonical event identity/provenance remains untouched. */
@FunctionalInterface
public interface FactionContextProjectionSource {
    Set<ContentContext> resolve(UUID entityId, Set<ContentContext> canonical);
    static FactionContextProjectionSource canonical() { return (id, contexts) -> Set.copyOf(contexts); }

    static Set<ContentContext> projectable() {
        return Set.of(ContentContext.CORRUPTION, ContentContext.DUNGEON, ContentContext.INVASION,
                ContentContext.WORLD_BOSS, ContentContext.EVENT_MOB, ContentContext.QUEST_MOB);
    }
    static Set<ContentContext> validate(Set<ContentContext> canonical, Set<ContentContext> effective) {
        final Set<ContentContext> copy = Set.copyOf(effective);
        for (final ContentContext context : ContentContext.values()) {
            if (!projectable().contains(context) && canonical.contains(context) != copy.contains(context)) {
                throw new IllegalArgumentException("Canonical-only faction context changed");
            }
        }
        return copy;
    }
    /** Unavailable semantic projection must not grant a truce or scripted-content exemption. */
    static Set<ContentContext> unavailable(Set<ContentContext> canonical) {
        final Set<ContentContext> result = new HashSet<>(canonical); result.addAll(projectable());
        return Set.copyOf(result);
    }
}
