package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;

import java.util.List;
import java.util.Locale;

/**
 * Faction reputation: a config-driven relation
 * matrix (ALLY / NEUTRAL / ENEMY) plus a dynamic ENEMY state whenever the two
 * factions are in an active raid. The relation drives a market price modifier —
 * trading with an enemy faction carries a surcharge (which burns, a money sink),
 * trading with an ally gives a discount.
 */
public final class FactionRelationManager {

    public enum Relation { ALLY, NEUTRAL, ENEMY }

    private final ConfigManager configManager;
    private final RaidManager raidManager;

    public FactionRelationManager(final ConfigManager configManager, final RaidManager raidManager) {
        this.configManager = configManager;
        this.raidManager = raidManager;
    }

    /**
     * Gets the relation between two factions: ENEMY if they are raiding each
     * other right now, otherwise the configured static relation (default NEUTRAL).
     * A missing faction represents a Menedék guest rather than an implicit NEUTRAL
     * citizen, so any relation involving {@code null} is always NEUTRAL.
     *
     * @param a one faction
     * @param b the other faction
     * @return the relation
     */
    public Relation getRelation(final FactionType a, final FactionType b) {
        if (a == null || b == null) {
            return Relation.NEUTRAL;
        }
        if (a == b) {
            return Relation.ALLY;
        }

        if (raidManager.isAtWar(a, b)) {
            return Relation.ENEMY;
        }

        final List<String> enemies = configManager.getStringList("factions.relations." + a.name().toLowerCase(Locale.ROOT) + ".enemies");
        if (enemies.stream().anyMatch(name -> name.equalsIgnoreCase(b.name()))) {
            return Relation.ENEMY;
        }

        final List<String> allies = configManager.getStringList("factions.relations." + a.name().toLowerCase(Locale.ROOT) + ".allies");
        if (allies.stream().anyMatch(name -> name.equalsIgnoreCase(b.name()))) {
            return Relation.ALLY;
        }

        return Relation.NEUTRAL;
    }

    /**
     * Gets the market price multiplier the buyer pays when buying from a seller
     * of the given faction (1.0 = no change; >1 enemy surcharge; <1 ally discount).
     *
     * @param buyerFaction the buyer's faction
     * @param sellerFaction the seller's faction
     * @return the price multiplier
     */
    public double getMarketPriceMultiplier(final FactionType buyerFaction, final FactionType sellerFaction) {
        return switch (getRelation(buyerFaction, sellerFaction)) {
            case ENEMY -> 1.0D + (Math.max(0.0D, configManager.getDouble("factions.relations.enemy-market-surcharge-percent", 25.0D)) / 100.0D);
            case ALLY -> 1.0D - (Math.max(0.0D, Math.min(90.0D, configManager.getDouble("factions.relations.ally-market-discount-percent", 10.0D))) / 100.0D);
            case NEUTRAL -> 1.0D;
        };
    }
}
