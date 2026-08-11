package hu.taliann.icesmp.classrelic;

/**
 * Szemantikus gameplay-esemény szótár a Resonance-routinghoz. A class rework ezt a
 * szerződést szolgáltatja majd; a relic-réteg soha nem spell-id-re, hanem eseményre
 * és tagre köt, így a képesség-implementációk cseréje nem töri a relic-mechanikát.
 */
public enum ClassGameplayEvent {
    ABILITY_RESOLVED,
    RESOURCE_SPENT,
    RESOURCE_FULL,
    DAMAGE_DEALT,
    DAMAGE_TAKEN,
    HEAL_RESOLVED,
    OVERHEAL,
    BLOCK,
    DODGE,
    SUMMON,
    FORM_CHANGED,
    MOVEMENT_ABILITY,
    KILL,
    LOW_HEALTH_ENTERED
}
