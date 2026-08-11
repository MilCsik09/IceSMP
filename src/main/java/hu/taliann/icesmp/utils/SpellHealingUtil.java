package hu.taliann.icesmp.utils;

import hu.taliann.icesmp.spells.CastModifiers;
import hu.taliann.icesmp.spells.SpellExecutionContext;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;

/** Common, modifier-aware healing primitive for spell outputs. */
public final class SpellHealingUtil {

    private SpellHealingUtil() {
    }

    /** Heals with the current synchronous cast context. */
    public static double heal(final LivingEntity target, final double baseAmount) {
        return heal(target, baseAmount, SpellExecutionContext.current());
    }

    /**
     * Heals with an explicit immutable snapshot. Use this overload after a
     * scheduler hop or for a delayed/channel effect.
     *
     * @return the effective health restored after max-health clamping
     */
    public static double heal(final LivingEntity target, final double baseAmount, final CastModifiers modifiers) {
        if (target == null || !Double.isFinite(baseAmount) || baseAmount <= 0.0D) {
            return 0.0D;
        }
        final CastModifiers effective = modifiers == null ? CastModifiers.IDENTITY : modifiers;
        final double scaledAmount = baseAmount * effective.healingMultiplier();
        if (!Double.isFinite(scaledAmount) || scaledAmount <= 0.0D) {
            return 0.0D;
        }
        final AttributeInstance maxHealth = target.getAttribute(Attribute.MAX_HEALTH);
        final double cap = maxHealth != null ? maxHealth.getValue() : 20.0D;
        final double before = target.getHealth();
        final double after = Math.min(cap, before + scaledAmount);
        target.setHealth(after);
        return Math.max(0.0D, after - before);
    }
}
