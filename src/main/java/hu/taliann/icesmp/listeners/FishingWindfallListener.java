package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.items.MoneyPouchItemFactory;
import hu.taliann.icesmp.managers.AfkManager;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Horgász-szerencse: kis eséllyel a fogás mellé egy iszapba veszett Kopott erszény is
 * akad a horogra — FIZIKAI tárgy, véletlen frakció-valutával (lore: a Bokic mindent
 * visz… és mindent visszahoz). A beváltás a MoneyPouchListener dolga; AFK-jelölt
 * játékosnak nem jár (auto-farm fék). Folia: a fish-event a játékos saját
 * régió-szálán fut. Minden kulcs élőben olvasódik (fishing-windfall.*).
 */
public final class FishingWindfallListener implements Listener {

    private final ConfigManager configManager;
    private final MoneyPouchItemFactory pouchFactory;
    private final AfkManager afkManager;
    private final MessageManager messageManager;

    public FishingWindfallListener(final ConfigManager configManager, final MoneyPouchItemFactory pouchFactory,
                                   final AfkManager afkManager, final MessageManager messageManager) {
        this.configManager = configManager;
        this.pouchFactory = pouchFactory;
        this.afkManager = afkManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCatch(final PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH
                || !configManager.getBoolean("fishing-windfall.enabled", true)) {
            return;
        }
        final Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        if (afkManager != null && afkManager.isAfk(player.getUniqueId())) {
            return; // AFK-horgász nem talál erszényt.
        }
        final double chance = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("fishing-windfall.chance-percent", 4.0D)));
        if (ThreadLocalRandom.current().nextDouble(100.0D) >= chance) {
            return;
        }
        final double min = Math.max(0.01D, configManager.getDouble("fishing-windfall.min-amount", 5.0D));
        final double max = Math.max(min, configManager.getDouble("fishing-windfall.max-amount", 15.0D));
        final long amount = Math.max(1L, Math.round(min + ThreadLocalRandom.current().nextDouble() * (max - min)));
        // Napi keret (fishing-windfall.daily-cap, 0 = korlátlan) — az auto-klikkes
        // horgász-farm féke; a PDC-számláló a játékos saját régió-szálán íródik.
        final double cap = configManager.getDouble("fishing-windfall.daily-cap", 150.0D);
        if (cap > 0.0D) {
            final org.bukkit.NamespacedKey dayKey = org.bukkit.NamespacedKey.fromString("icesmp:windfall_day");
            final org.bukkit.NamespacedKey sumKey = org.bukkit.NamespacedKey.fromString("icesmp:windfall_sum");
            final long today = System.currentTimeMillis() / 86_400_000L;
            final long storedDay = player.getPersistentDataContainer()
                    .getOrDefault(dayKey, org.bukkit.persistence.PersistentDataType.LONG, -1L);
            final long earned = storedDay == today ? player.getPersistentDataContainer()
                    .getOrDefault(sumKey, org.bukkit.persistence.PersistentDataType.LONG, 0L) : 0L;
            if (earned + amount > (long) cap) {
                return;
            }
            player.getPersistentDataContainer().set(dayKey, org.bukkit.persistence.PersistentDataType.LONG, today);
            player.getPersistentDataContainer().set(sumKey, org.bukkit.persistence.PersistentDataType.LONG, earned + amount);
        }
        final ItemStack pouch = pouchFactory.createRandom(amount);
        for (final ItemStack overflow : player.getInventory().addItem(pouch).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7F, 1.3F);
        player.sendMessage(messageManager.getMessage("fishing-windfall",
                "<gold>💰 Egy iszapba veszett erszény is a horogra akadt — nézd meg, mi van benne!</gold>"));
    }
}
