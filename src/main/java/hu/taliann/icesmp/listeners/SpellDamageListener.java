package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.itemization.EquipmentProficiencyService;
import hu.taliann.icesmp.itemization.ItemTemplate;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.utils.MessageManager;
import hu.taliann.icesmp.utils.SpellDamageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * A mágia damage-type és spell-projectile snapshotok kiszolgálása. Canonical armor
 * resistance csak ACTIVE equipmentből jöhet; BASIC/NOT_MANAGED gear megőrzi a legacy policyt.
 */
public final class SpellDamageListener implements Listener {

    private final ConfigManager configManager;
    private final MessageManager messageManager;

    public SpellDamageListener(final ConfigManager configManager, final MessageManager messageManager) {
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpellProjectileDamage(final EntityDamageByEntityEvent event) {
        final double multiplier = SpellDamageUtil.projectileDamageMultiplier(event.getDamager());
        if (multiplier == 1.0D) return;
        event.setDamage(Math.max(0.0D, event.getDamage() * multiplier));
    }

    @EventHandler(ignoreCancelled = true)
    public void onMagicDamage(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        final hu.taliann.icesmp.data.SpellSchool school = SpellDamageUtil.schoolOf(event.getDamageSource());
        if (school == null) return;
        final double perLevel = Math.max(0.0D, configManager.getDouble("spells.magic-resist.per-level", 0.08D));
        final double schoolPerLevel = Math.max(0.0D, configManager.getDouble("spells.magic-resist.school-per-level", 0.10D));
        final double cap = Math.min(1.0D, Math.max(0.0D,
                configManager.getDouble("spells.magic-resist.max-reduction", 0.6D)));

        final Enchantment runavert = resolveEnchant("icesmp:runavert");
        final Enchantment schoolCounter = school.resistEnchantId() == null
                ? null : resolveEnchant("icesmp:" + school.resistEnchantId());
        int genericLevels = 0;
        int schoolLevels = 0;
        int[] levels = resistanceLevels(victim, victim.getInventory().getHelmet(), ItemTemplate.Slot.HEAD,
                runavert, schoolCounter);
        genericLevels += levels[0]; schoolLevels += levels[1];
        levels = resistanceLevels(victim, victim.getInventory().getChestplate(), ItemTemplate.Slot.CHEST,
                runavert, schoolCounter);
        genericLevels += levels[0]; schoolLevels += levels[1];
        levels = resistanceLevels(victim, victim.getInventory().getLeggings(), ItemTemplate.Slot.LEGS,
                runavert, schoolCounter);
        genericLevels += levels[0]; schoolLevels += levels[1];
        levels = resistanceLevels(victim, victim.getInventory().getBoots(), ItemTemplate.Slot.FEET,
                runavert, schoolCounter);
        genericLevels += levels[0]; schoolLevels += levels[1];

        final double reduction = Math.min(cap, genericLevels * perLevel + schoolLevels * schoolPerLevel);
        if (reduction <= 0.0D) return;
        event.setDamage(event.getDamage() * (1.0D - reduction));
    }

    private static int[] resistanceLevels(final Player player, final ItemStack item,
                                          final ItemTemplate.Slot slot,
                                          final Enchantment generic,
                                          final Enchantment schoolCounter) {
        if (item == null || !item.hasItemMeta()
                || !EquipmentProficiencyService.allowsGameplayContribution(player, item, slot)) {
            return new int[] {0, 0};
        }
        return new int[] {
                generic == null ? 0 : item.getEnchantmentLevel(generic),
                schoolCounter == null ? 0 : item.getEnchantmentLevel(schoolCounter)
        };
    }

    @EventHandler
    public void onMagicDeath(final PlayerDeathEvent event) {
        final EntityDamageEvent last = event.getEntity().getLastDamageCause();
        final hu.taliann.icesmp.data.SpellSchool school = last == null
                ? null : SpellDamageUtil.schoolOf(last.getDamageSource());
        if (school == null) return;
        final Player killer = event.getEntity().getKiller();
        final Component message = killer != null
                ? messageManager.getMessage("spell-death-by-caster",
                        "<gray>{killer} elemésztette {victim} életét a(z) {school} erejével.</gray>",
                        Map.of("victim", event.getEntity().getName(), "killer", killer.getName(),
                                "school", school.getDisplayName()))
                : messageManager.getMessage("spell-death",
                        "<gray>{victim} életét elemésztette a(z) {school}.</gray>",
                        Map.of("victim", event.getEntity().getName(), "school", school.getDisplayName()));
        event.deathMessage(message);
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
