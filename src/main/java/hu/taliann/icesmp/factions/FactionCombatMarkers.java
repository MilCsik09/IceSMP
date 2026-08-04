package hu.taliann.icesmp.factions;

import org.bukkit.NamespacedKey;

import java.util.Objects;

/** Canonical PDC keys shared by combat-content producers and the passive resolver. */
public final class FactionCombatMarkers {

    public static final NamespacedKey CORRUPTION_MOB = key("corruption_mob");
    public static final NamespacedKey DUNGEON_COMBAT = key("faction_combat_dungeon");
    public static final NamespacedKey SCRIPTED_COMBAT = key("scripted_combat");
    public static final NamespacedKey EVENT_MOB = key("event_mob");
    public static final NamespacedKey CROWN_CURSE_TARGET = key("crown_curse_target");

    private FactionCombatMarkers() {
    }

    private static NamespacedKey key(final String value) {
        return Objects.requireNonNull(NamespacedKey.fromString("icesmp:" + value));
    }
}
