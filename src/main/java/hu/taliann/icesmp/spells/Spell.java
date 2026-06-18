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

    /**
     * Refunds a previously consumed cost. Called only when {@link #executeSpell}
     * reports a no-op (no effect was applied), restoring the caster's resource —
     * the inverse of {@link #consumeCost(Player)} for the standard cost types.
     */
    default void refundCost(final Player player) {
        switch (getCostType()) {
            case HUNGER -> player.setFoodLevel(Math.min(20, player.getFoodLevel() + Math.max(0, getCostAmount())));
            case XP -> {
                final int currentXP = ExperienceUtil.getTotalExperience(player);
                ExperienceUtil.setTotalExperience(player, currentXP + Math.max(0, getCostAmount()));
            }
            case HEALTH -> {
                final org.bukkit.attribute.AttributeInstance maxHealth =
                        player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                final double cap = maxHealth != null ? maxHealth.getValue() : 20.0D;
                player.setHealth(Math.min(cap, player.getHealth() + Math.max(0, getCostAmount())));
            }
        }
    }

    void execute(Player player);

    /**
     * Executes the spell and reports whether the effect actually fired. Spells that
     * can no-op (no target in range, no companions, …) override this and return false
     * so the caster is not charged the cost or put on cooldown for a wasted cast.
     * The default simply runs {@link #execute(Player)} and reports success.
     *
     * @return true if the spell's effect was applied
     */
    default boolean executeSpell(final Player player) {
        execute(player);
        return true;
    }
}

