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

    private static final NamespacedKey RUNAVERT_KEY = NamespacedKey.fromString("icesmp:runavert");

    private final ConfigManager configManager;
    private final MessageManager messageManager;

    public SpellDamageListener(final ConfigManager configManager, final MessageManager messageManager) {
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMagicDamage(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !SpellDamageUtil.isMagicDamage(event.getDamageSource())) {
            return;
        }
        final Enchantment runavert = resolveRunavert();
        if (runavert == null) {
            return;
        }
        int levels = 0;
        for (final ItemStack armor : victim.getInventory().getArmorContents()) {
            if (armor != null && armor.hasItemMeta()) {
                levels += armor.getEnchantmentLevel(runavert);
            }
        }
        if (levels <= 0) {
            return;
        }
        final double perLevel = Math.max(0.0D, configManager.getDouble("spells.magic-resist.per-level", 0.08D));
        final double cap = Math.min(1.0D, Math.max(0.0D, configManager.getDouble("spells.magic-resist.max-reduction", 0.6D)));
        final double reduction = Math.min(cap, levels * perLevel);
        if (reduction <= 0.0D) {
            return;
        }
        event.setDamage(event.getDamage() * (1.0D - reduction));
        victim.sendActionBar(messageManager.getMessage("spell-resist-notice",
                "<light_purple>✦ A Rúnavért elnyelte a mágia egy részét (−{percent}%).</light_purple>",
                Map.of("percent", String.valueOf(Math.round(reduction * 100.0D)))));
    }

    @EventHandler
    public void onMagicDeath(final PlayerDeathEvent event) {
        final EntityDamageEvent last = event.getEntity().getLastDamageCause();
        if (last == null || !SpellDamageUtil.isMagicDamage(last.getDamageSource())) {
            return;
        }
        // A saját damage-type message-id-jét a kliens nem tudná fordítani — magyar üzenet
        // szerver-oldalról. Ha volt gyilkos, nevesítjük.
        final Player killer = event.getEntity().getKiller();
        final Component message = killer != null
                ? messageManager.getMessage("spell-death-by-caster",
                        "<gray>{victim} elemésztette {killer} mágiája.</gray>",
                        Map.of("victim", event.getEntity().getName(), "killer", killer.getName()))
                : messageManager.getMessage("spell-death",
                        "<gray>{victim} elemésztette az ősmágia.</gray>",
                        Map.of("victim", event.getEntity().getName()));
        event.deathMessage(message);
    }

    /** A Rúnavért enchant a registryből (null, ha a bootstrap-regisztráció hiányzik). */
    private static Enchantment resolveRunavert() {
        try {
            return io.papermc.paper.registry.RegistryAccess.registryAccess()
                    .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT)
                    .get(RUNAVERT_KEY);
        } catch (final Exception exception) {
            return null;
        }
    }
}
