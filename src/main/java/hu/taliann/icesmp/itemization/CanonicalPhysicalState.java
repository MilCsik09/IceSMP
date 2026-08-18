package hu.taliann.icesmp.itemization;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Objects;

/**
 * Explicit mutation render preservation boundary.
 *
 * <p>Canonical identity, lore, attributes, enchants and all authored metadata come exclusively from
 * the freshly rendered stack. Only physical wear is copied from the old physical witness, so a
 * reroll/rune/ascension cannot repair an item for free and stale/illegal ItemMeta cannot be
 * laundered back into canonical state.</p>
 */
public final class CanonicalPhysicalState {
    private CanonicalPhysicalState() { }

    public static ItemStack preserve(final ItemStack previous, final ItemStack rendered) {
        Objects.requireNonNull(rendered, "rendered");
        if (previous == null || previous.getType().isAir() || rendered.getType().isAir()) {
            return rendered;
        }
        final ItemMeta previousMeta = previous.getItemMeta();
        final ItemMeta renderedMeta = rendered.getItemMeta();
        if (previousMeta instanceof Damageable oldDamage && renderedMeta instanceof Damageable newDamage) {
            newDamage.setDamage(Math.max(0, oldDamage.getDamage()));
            rendered.setItemMeta((ItemMeta) newDamage);
        }
        return rendered;
    }
}
