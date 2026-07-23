package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.utils.MessageManager;
import hu.taliann.icesmp.utils.SpellDamageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * A mágia damage-type (icesmp:magia) kiszolgálása:
 * <ul>
 *   <li><b>Rúnavért ellenállás:</b> a viselt páncélon lévő Rúnavért-szintek összege
 *       szintenként {@code spells.magic-resist.per-level} arányban csökkenti a bejövő
 *       SPELL-sebzést (plafon: {@code max-reduction}) — vanília sebzésre nem hat.</li>
 *   <li><b>Magyar halál-üzenet:</b> a saját damage-type fordítási kulcsát a kliens nem
 *       ismerné — mágia-halálnál a halál-üzenetet szerver-oldalon írjuk felül.</li>
 * </ul>
 * Minden kulcs élőben olvasódik. A damage-event az áldozat régió-szálán fut — a
 * páncél-olvasás ott biztonságos (Folia).
 */
public final class SpellDamageListener implements Listener {

    private final ConfigManager configManager;
    private final MessageManager messageManager;

    public SpellDamageListener(final ConfigManager configManager, final MessageManager messageManager) {
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMagicDamage(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        final hu.taliann.icesmp.data.SpellSchool school = SpellDamageUtil.schoolOf(event.getDamageSource());
        if (school == null) {
            return;
        }
        final double perLevel = Math.max(0.0D, configManager.getDouble("spells.magic-resist.per-level", 0.08D));
        final double schoolPerLevel = Math.max(0.0D, configManager.getDouble("spells.magic-resist.school-per-level", 0.10D));
        final double cap = Math.min(1.0D, Math.max(0.0D, configManager.getDouble("spells.magic-resist.max-reduction", 0.6D)));

        // Rúnavért: generikus, MINDEN iskola ellen; iskola-counter (pl. Fagypáncél a
        // fagymágia, Főnixtoll a tűzmágia ellen): csak a találat iskolájára hat, de
        // szintenként erősebb — a kettő összeadódik, a közös plafonig.
        final Enchantment runavert = resolveEnchant("icesmp:runavert");
        final Enchantment schoolCounter = school.resistEnchantId() == null
                ? null : resolveEnchant("icesmp:" + school.resistEnchantId());
        int genericLevels = 0;
        int schoolLevels = 0;
        for (final ItemStack armor : victim.getInventory().getArmorContents()) {
            if (armor == null || !armor.hasItemMeta()) {
                continue;
            }
            if (runavert != null) {
                genericLevels += armor.getEnchantmentLevel(runavert);
            }
            if (schoolCounter != null) {
                schoolLevels += armor.getEnchantmentLevel(schoolCounter);
            }
        }
        final double reduction = Math.min(cap, genericLevels * perLevel + schoolLevels * schoolPerLevel);
        if (reduction <= 0.0D) {
            return;
        }
        // Szándékosan NINCS kiírás: sok találatnál az action-bar spam zavaróbb, mint
        // hasznos — a csökkentett sebzés-számok magukért beszélnek (damage indicator).
        event.setDamage(event.getDamage() * (1.0D - reduction));
    }

    @EventHandler
    public void onMagicDeath(final PlayerDeathEvent event) {
        final EntityDamageEvent last = event.getEntity().getLastDamageCause();
        final hu.taliann.icesmp.data.SpellSchool school = last == null
                ? null : SpellDamageUtil.schoolOf(last.getDamageSource());
        if (school == null) {
            return;
        }
        // A saját damage-type message-id-jét a kliens nem tudná fordítani — magyar üzenet
        // szerver-oldalról, az ISKOLA nevével. Ha volt gyilkos, nevesítjük.
        final Player killer = event.getEntity().getKiller();
        final Component message = killer != null
                ? messageManager.getMessage("spell-death-by-caster",
                        "<gray>{victim} elemésztette {killer} {school}ja.</gray>",
                        Map.of("victim", event.getEntity().getName(), "killer", killer.getName(),
                                "school", school.getDisplayName()))
                : messageManager.getMessage("spell-death",
                        "<gray>{victim} elemésztette a {school}.</gray>",
                        Map.of("victim", event.getEntity().getName(), "school", school.getDisplayName()));
        event.deathMessage(message);
    }

    /** Enchant a registryből kulcs alapján (null, ha a bootstrap-regisztráció hiányzik). */
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
