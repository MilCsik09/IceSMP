package hu.taliann.icesmp.classspec.integration;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Version-independent presentation boundary for class/spec HUD state.
 *
 * <p>The class/spec domain publishes already-computed values through this port. Implementations may
 * render them with the first-party IceSMP HUD or its native compact fallback, but they must never derive gameplay values or
 * own progression.</p>
 */
public interface ClassSpecHudPort {

    void activate(UUID playerId, String layoutId);

    void publish(UUID playerId, Map<String, String> fields, Set<String> dirtyFields);

    void popup(UUID playerId, String popupId, Map<String, String> arguments);

    void clear(UUID playerId);
}
