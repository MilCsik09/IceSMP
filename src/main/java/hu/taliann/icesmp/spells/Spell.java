package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.ExperienceUtil;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface Spell {

    String getId();

    String getName();

    int getCooldown();

    SpellCostType getCostType();

    int getCostAmount();

    /**
     * The class-resource (power) cost of casting this spell — the WoW-style model where the spell
     * spends the class pool (Mana / Düh / Energia …) shown on the HUD, which regenerates over time.
     * Derived from the cooldown tier so spammable (low-cooldown) spells are gated mainly by the
     * resource, while big-cooldown spells are gated mainly by their cooldown. A spell may override
     * this for a custom cost. The legacy {@link #getCostType()}/{@link #getCostAmount()} are only
     * used when the resource system is disabled in config.
     *
     * @return the resource cost (0–max)
     */
    default int getResourceCost() {
        final int cooldown = getCooldown();
        if (cooldown <= 0) {
            return 15;
        }
        if (cooldown <= 30) {
            return 20;
        }
        if (cooldown <= 90) {
            return 30;
        }
        if (cooldown <= 180) {
            return 40;
        }
        return 50;
    }

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

    /**
     * Casts the spell with a spell-mastery power multiplier (1.0 = base). The
     * default ignores the multiplier and delegates to {@link #executeSpell(Player)},
     * so only spells that support scaling (the configured spells) override it —
     * every hand-written stateful spell keeps working unchanged.
     *
     * @param player the caster
     * @param powerMultiplier the mastery power multiplier (>= 1.0)
     * @return true if the spell's effect was applied
     */
    default boolean executeSpell(final Player player, final double powerMultiplier) {
        return executeSpell(player);
    }

    /**
     * Human-facing effect description lines (damage, range, effects, …) shown in the
     * spellbook. Configured spells auto-generate precise numbers from their stats; the
     * default is empty so callers can fall back to a messages.yml description.
     */
    default java.util.List<String> describe() {
        return java.util.List.of();
    }

    /**
     * Clears any per-player session state this spell holds (effects, restore-snapshots,
     * cooldown flags). Called for every registered spell on logout/kick, so the session
     * cleanup no longer hardcodes the stateful spells — a new stateful spell only needs
     * to override this. The default is a no-op for the (majority) stateless spells.
     *
     * @param playerId the player whose state to clear
     */
    default void clearPlayerState(final UUID playerId) {
    }
}

