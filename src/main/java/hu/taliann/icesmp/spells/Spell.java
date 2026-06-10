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
        }
    }

    void execute(Player player);
}

