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

    private static final NamespacedKey SIGNATURE_KEY = SignatureItemListener.SIGNATURE_PDC_KEY;

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
        handleBorderCross(event);
    }

    /**
     * Enderpearl / chorus / portál / plugin-teleport is határátlépés — a PlayerTeleportEvent
     * KÜLÖN handler-lista, a sima move-handler nem kapja meg, ezért itt is őrködünk
     * (a PlayerPortalEvent a teleport-listán fut, így azt is lefedi).
     */
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(final org.bukkit.event.player.PlayerTeleportEvent event) {
        handleBorderCross(event);
    }

    /**
     * Jármű (csónak/csille) nem vált ki PlayerMoveEvent-et az utasnál — a kapu itt is zár:
     * a kulcs nélküli utast kiszállítjuk és visszatesszük a határ elé (a VehicleMoveEvent
     * nem cancellable). A jármű régió-szálán futunk; az utas ugyanabban a régióban van.
     */
    @EventHandler
    public void onVehicleMove(final org.bukkit.event.vehicle.VehicleMoveEvent event) {
        final Location from = event.getFrom();
        final Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        if (!configManager.getBoolean("territory.dungeon.enabled", true)) {
            return;
        }
        for (final org.bukkit.entity.Entity passenger : event.getVehicle().getPassengers()) {
            if (passenger instanceof Player player && !mayCross(player, from, to)) {
                event.getVehicle().eject();
                player.teleportAsync(from);
            }
        }
    }

    private void handleBorderCross(final PlayerMoveEvent event) {
        final Location from = event.getFrom();
        final Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        if (!configManager.getBoolean("territory.dungeon.enabled", true)) {
            return;
        }
        if (!mayCross(event.getPlayer(), from, to)) {
            event.setCancelled(true);
        }
    }

    /**
     * A kapu-logika közös magja (move/teleport/jármű): true = szabad az út (nem kazamata,
     * bent mozgás, admin, élő passz, vagy sikeres kulcs-beváltás), false = a határ zárva.
     * A kulcs-fogyasztás és az üzenetek mellékhatásként itt történnek — a játékos saját
     * (vagy vele azonos) régió-szálán futunk.
     */
    private boolean mayCross(final Player player, final Location from, final Location to) {
        final Territory target = territoryManager.getTerritoryAt(to);
        if (target == null || target.type() != TerritoryType.DUNGEON) {
            return true;
        }
        final Territory previous = territoryManager.getTerritoryAt(from);
        if (previous != null && previous.id().equals(target.id())) {
            return true; // Bent mozgás — a kapu csak a határon őrködik.
        }
        if (player.hasPermission(hu.taliann.icesmp.managers.TerritoryProtectionService.ADMIN_BYPASS)) {
            return true;
        }
        final String zoneId = target.id().toLowerCase(Locale.ROOT);
        final NamespacedKey passKey = new NamespacedKey(plugin, "dungeon_pass_" + zoneId);
        final NamespacedKey lockKey = new NamespacedKey(plugin, "dungeon_lock_" + zoneId);
        final long now = System.currentTimeMillis();

        // 1) Aktív passz: a futam alatt szabad a ki-be járás.
        final Long passUntil = player.getPersistentDataContainer().get(passKey, PersistentDataType.LONG);
        if (passUntil != null && passUntil > now) {
            return true;
        }
        // 2) Heti pecsét: amíg él, új futam nem kezdhető.
        final Long lockUntil = player.getPersistentDataContainer().get(lockKey, PersistentDataType.LONG);
        if (lockUntil != null && lockUntil > now) {
            final long daysLeft = Math.max(1L, (lockUntil - now + 86_399_999L) / 86_400_000L);
            player.sendActionBar(messageManager.getMessage("dungeon-locked",
                    "<red>⛨ {name} pecsétje még friss rajtad — {days} nap múlva térhetsz vissza.</red>",
                    Map.of("name", target.name(), "days", String.valueOf(daysLeft))));
            return false;
        }
        // 3) Kulcs: elfogy, passz + pecsét kerül fel.
        final ItemStack key = findKey(player, zoneId);
        if (key == null) {
            player.sendActionBar(messageManager.getMessage("dungeon-no-key",
                    "<red>⛨ {name} kapuja zárva — a Mélység Népe csarnokába kulcs kell.</red>",
                    Map.of("name", target.name())));
            return false;
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

        // Csoport-kedvezmény: EGY kulcs a közelben álló párttagoknak is nyit (passz+pecsét) —
        // az 5 fős túra nem kér 5 kulcsot. A tagok PDC-írása a saját régió-szálukon fut.
        if (configManager.getBoolean("territory.dungeon.party-shared-key", true) && partyManagerRef != null) {
            final hu.taliann.icesmp.managers.PartyManager.Party party =
                    partyManagerRef.getParty(player.getUniqueId());
            if (party != null) {
                final long passUntilShared = now + passHours * 3_600_000L;
                final long lockUntilShared = lockDays > 0L ? now + lockDays * 86_400_000L : 0L;
                for (final java.util.UUID memberId : party.getMembers()) {
                    if (memberId.equals(player.getUniqueId())) {
                        continue;
                    }
                    final Player member = org.bukkit.Bukkit.getPlayer(memberId);
                    if (member == null) {
                        continue;
                    }
                    try {
                        if (!member.getWorld().equals(player.getWorld())
                                || member.getLocation().distanceSquared(player.getLocation()) > 16.0D * 16.0D) {
                            continue;
                        }
                    } catch (final Exception ignored) {
                        // Kereszt-régiós olvasás nem elérhető Folián — nem közelinek vesszük
                        // (a PartyManager ugyanezt a védőhálót használja).
                        continue;
                    }
                    member.getScheduler().run(plugin, task -> {
                        // A saját friss pecsétje alatt álló tag nem kap új passzt (a heti
                        // zár rá is érvényes).
                        final Long memberLock = member.getPersistentDataContainer().get(lockKey, PersistentDataType.LONG);
                        if (memberLock != null && memberLock > System.currentTimeMillis()) {
                            return;
                        }
                        member.getPersistentDataContainer().set(passKey, PersistentDataType.LONG, passUntilShared);
                        if (lockUntilShared > 0L) {
                            member.getPersistentDataContainer().set(lockKey, PersistentDataType.LONG, lockUntilShared);
                        }
                        member.sendMessage(messageManager.getMessage("dungeon-party-entry",
                                "<gold>🗝 A csapatod kulcsa neked is utat nyitott — {name} vár.</gold>",
                                Map.of("name", target.name())));
                    }, null);
                }
            }
        }
        return true;
    }

    private volatile hu.taliann.icesmp.managers.PartyManager partyManagerRef;

    public void setPartyManager(final hu.taliann.icesmp.managers.PartyManager partyManager) {
        this.partyManagerRef = partyManager;
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
