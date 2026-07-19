package hu.taliann.icesmp.utils;

import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Wither;
import org.bukkit.entity.Zoglin;
import org.bukkit.entity.Zombie;

/**
 * Shared undead classification — the single source of truth used by the DARK faction
 * passive (undead ignore the marked) and the soul-drop rules (the Queen's word carries
 * no harvestable soul). Keep the type list in sync with the lore's "élőhalott" notion.
 */
public final class UndeadUtil {

    private UndeadUtil() {
    }

    public static boolean isUndead(final Object entity) {
        return entity instanceof Zombie
                || entity instanceof AbstractSkeleton
                || entity instanceof Phantom
                || entity instanceof Zoglin
                || entity instanceof Wither;
    }
}
