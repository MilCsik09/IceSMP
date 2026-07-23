package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.SpellSchool;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Egy páncélon EGY iskola-counter enchant élhet (Fagypáncél, Főnixtoll, Éj-fátyol,
 * Árnyűző, Méregfojtó, Viharfogó, Káosz-zabla) — az iskola-választás build-döntés,
 * nem gyűjtögetés. Az üllő-eredményt nullázzuk, ha kettő kerülne össze; a generikus
 * Rúnavért bármelyik mellett élhet. (A registry-oldali exclusive-set helyett üllő-őr:
 * ez a stabil API-felület.)
 */
public final class SchoolCounterAnvilListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareAnvil(final PrepareAnvilEvent event) {
        final ItemStack result = event.getResult();
        if (result == null || !result.hasItemMeta()) {
            return;
        }
        int counters = 0;
        for (final SpellSchool school : SpellSchool.values()) {
            final String id = school.resistEnchantId();
            if (id == null) {
                continue;
            }
            final Enchantment enchantment = resolveEnchant("icesmp:" + id);
            if (enchantment != null && result.getEnchantmentLevel(enchantment) > 0) {
                counters++;
            }
        }
        if (counters > 1) {
            event.setResult(null);
        }
    }

    private static Enchantment resolveEnchant(final String key) {
        try {
            return io.papermc.paper.registry.RegistryAccess.registryAccess()
                    .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT)
                    .get(NamespacedKey.fromString(key));
        } catch (final Exception exception) {
            return null;
        }
    }
}
