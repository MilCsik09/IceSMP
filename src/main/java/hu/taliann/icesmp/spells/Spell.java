package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.ExperienceUtil;
import org.bukkit.entity.Player;

public interface Spell {

    String getId();

    String getName();

    int getCooldown();

    SpellCostType getCostType();

    int getCostAmount();

    default int getCooldownDelay() {
        return 0;
    }

    default boolean canCast(final Player player) {
        return true;
    }

    default boolean hasRequiredCost(final Player player) {
        return switch (getCostType()) {
            case HUNGER -> player.getFoodLevel() >= getCostAmount();
            case XP -> ExperienceUtil.getTotalExperience(player) >= getCostAmount();
            // Must survive the cast: keep the player strictly above the cost.
            case HEALTH -> player.getHealth() > getCostAmount();
        };
    }

    default void consumeCost(final Player player) {
        switch (getCostType()) {
            case HUNGER -> player.setFoodLevel(Math.max(0, player.getFoodLevel() - getCostAmount()));
            case XP -> {
                final int currentXP = ExperienceUtil.getTotalExperience(player);
                final int newXP = Math.max(0, currentXP - Math.max(0, getCostAmount()));
                ExperienceUtil.setTotalExperience(player, newXP);
            }
            // hasRequiredCost guarantees health > cost, so the result stays positive.
            case HEALTH -> player.setHealth(Math.max(0.5D, player.getHealth() - getCostAmount()));
        }
    }

    void execute(Player player);
}

