package hu.taliann.icesmp.spells;

/**
 * What a spell costs to cast.
 * <ul>
 *   <li>{@code HUNGER} — food points (1 drumstick = 2): stamina/mobility/utility.</li>
 *   <li>{@code XP} — experience points: arcane / ranged power.</li>
 *   <li>{@code HEALTH} — health points (1 heart = 2): blood magic / self-sacrifice.</li>
 * </ul>
 */
public enum SpellCostType {
    HUNGER,
    XP,
    HEALTH
}
