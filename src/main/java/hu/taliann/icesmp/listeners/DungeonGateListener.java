package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.data.TerritoryType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;

/**
 * B3 — Kazamata-kapu (lore: a Mélység Népe elhagyott csarnokai — kódex I.). A
 * DUNGEON típusú territóriumba belépni csak kulccsal lehet: a kulcs egy
 * signature-tagelt tárgy ({@code dungeonkulcs_<zóna-id>} — boltból/receptből, az
 * admin definiálja), belépéskor ELFOGY, és két PDC-bélyeg keletkezik:
 * <ul>
 *   <li><b>passz</b> ({@code dungeon_pass_<id>}): pass-hours órán át szabad a
 *       ki-be járás (a futam alatt a határ nem kér új kulcsot);</li>
 *   <li><b>pecsét</b> ({@code dungeon_lock_<id>}): lockout-days napig új futam
 *       nem kezdhető (heti lockout — játékosonként; a party minden tagja saját
 *       kulccsal lép be).</li>
 * </ul>
 * A bent lévő mobok erejét a {@code territory.mob-rules.dungeon} sor adja
 * (fix bónusz-szint), a boss admin-eszközökkel (pl. /events worldboss a zónában)
 * telepíthető. Folia: a move-event a játékos saját régió-szálán fut.
 */
public final class DungeonGateListener implements Listener {

    private static final NamespacedKey SIGNATURE_KEY = NamespacedKey.fromString("icesmp:signature_item");

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final TerritoryManager territoryManager;
    private final MessageManager messageManager;

    public DungeonGateListener(final JavaPlugin plugin, final ConfigManager configManager,
                               final TerritoryManager territoryManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.territoryManager = territoryManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(final PlayerMoveEvent event) {
        final Location from = event.getFrom();
        final Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        if (!configManager.getBoolean("territory.dungeon.enabled", true)) {
            return;
        }
        final Territory target = territoryManager.getTerritoryAt(to);
        if (target == null || target.type() != TerritoryType.DUNGEON) {
            return;
        }
        final Territory previous = territoryManager.getTerritoryAt(from);
        if (previous != null && previous.id().equals(target.id())) {
            return; // Bent mozgás — a kapu csak a határon őrködik.
        }
        final Player player = event.getPlayer();
        if (player.hasPermission(hu.taliann.icesmp.managers.TerritoryProtectionService.ADMIN_BYPASS)) {
            return;
        }
        final String zoneId = target.id().toLowerCase(Locale.ROOT);
        final NamespacedKey passKey = new NamespacedKey(plugin, "dungeon_pass_" + zoneId);
        final NamespacedKey lockKey = new NamespacedKey(plugin, "dungeon_lock_" + zoneId);
        final long now = System.currentTimeMillis();

        // 1) Aktív passz: a futam alatt szabad a ki-be járás.
        final Long passUntil = player.getPersistentDataContainer().get(passKey, PersistentDataType.LONG);
        if (passUntil != null && passUntil > now) {
            return;
        }
        // 2) Heti pecsét: amíg él, új futam nem kezdhető.
        final Long lockUntil = player.getPersistentDataContainer().get(lockKey, PersistentDataType.LONG);
        if (lockUntil != null && lockUntil > now) {
            event.setCancelled(true);
            final long daysLeft = Math.max(1L, (lockUntil - now + 86_399_999L) / 86_400_000L);
            player.sendActionBar(messageManager.getMessage("dungeon-locked",
                    "<red>⛨ {name} pecsétje még friss rajtad — {days} nap múlva térhetsz vissza.</red>",
                    Map.of("name", target.name(), "days", String.valueOf(daysLeft))));
            return;
        }
        // 3) Kulcs: elfogy, passz + pecsét kerül fel.
        final ItemStack key = findKey(player, zoneId);
        if (key == null) {
            event.setCancelled(true);
            player.sendActionBar(messageManager.getMessage("dungeon-no-key",
                    "<red>⛨ {name} kapuja zárva — a Mélység Népe csarnokába kulcs kell.</red>",
                    Map.of("name", target.name())));
            return;
        }
        key.setAmount(key.getAmount() - 1);
        final long passHours = Math.max(1L, configManager.getLong("territory.dungeon.pass-hours", 2L));
        final long lockDays = Math.max(0L, configManager.getLong("territory.dungeon.lockout-days", 7L));
        player.getPersistentDataContainer().set(passKey, PersistentDataType.LONG, now + passHours * 3_600_000L);
        if (lockDays > 0L) {
            player.getPersistentDataContainer().set(lockKey, PersistentDataType.LONG, now + lockDays * 86_400_000L);
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_VAULT_OPEN_SHUTTER, 1.0F, 0.7F);
        player.sendMessage(messageManager.getMessage("dungeon-entered",
                "<gold>🗝 A kulcs porrá omlik a zárban — {name} megnyílt előtted ({hours} órád van; a pecsét {days} napig tart).</gold>",
                Map.of("name", target.name(), "hours", String.valueOf(passHours), "days", String.valueOf(lockDays))));
    }

    /** A zóna kulcsa a játékosnál: signature == dungeonkulcs_<zóna-id>. */
    private ItemStack findKey(final Player player, final String zoneId) {
        final String wanted = "dungeonkulcs_" + zoneId;
        for (final ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.hasItemMeta()
                    && wanted.equals(item.getItemMeta().getPersistentDataContainer()
                            .get(SIGNATURE_KEY, PersistentDataType.STRING))) {
                return item;
            }
        }
        return null;
    }
}
