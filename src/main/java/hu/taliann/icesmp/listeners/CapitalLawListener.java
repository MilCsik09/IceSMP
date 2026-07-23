package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.data.TerritoryType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.SinManager;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/**
 * K10 — Caldestera városi törvényei (a NEUTRAL főváros zónájában, mindenkire):
 * <ul>
 *   <li><b>Fegyvertilalom:</b> nyílt fegyvert (kard/balta/szigony/íj/számszeríj/buzogány)
 *       kézben tartani tilos — az őrség kiveszi a kézből (az inventoryba kerül vissza,
 *       NEM vész el). A <i>Bokic-menti Sétapálca</i> a kiskapu: bot, a szabály nem látja —
 *       pont ezért árulja a Botera-negyed feketepiaca.</li>
 *   <li><b>Körözött-kapu:</b> a vérdíjas (körözött) játékost az őrség a kapunál
 *       visszafordítja — hacsak nincs nála <i>Hamisított Menlevél</i> (feketepiac-áru).</li>
 * </ul>
 * Minden kulcs élőben olvasódik (territory.capital-law.*). Folia: a move/held event a
 * játékos saját régió-szálán fut — az inventory-írás és a zóna-lookup ott biztonságos.
 */
public final class CapitalLawListener implements Listener {

    private static final NamespacedKey SIGNATURE_KEY = SignatureItemListener.SIGNATURE_PDC_KEY;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final TerritoryManager territoryManager;
    private final SinManager sinManager;
    private final MessageManager messageManager;

    public CapitalLawListener(final JavaPlugin plugin, final ConfigManager configManager,
                              final TerritoryManager territoryManager, final SinManager sinManager,
                              final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.territoryManager = territoryManager;
        this.sinManager = sinManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(final PlayerMoveEvent event) {
        handleBorderCross(event);
    }

    /** Enderpearl/portál/plugin-teleport is határátlépés — a teleport KÜLÖN handler-lista. */
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(final org.bukkit.event.player.PlayerTeleportEvent event) {
        handleBorderCross(event);
    }

    /** Jármű-utas nem vált ki PlayerMoveEvent-et — a körözött utast a kapunál kiszállítjuk. */
    @EventHandler
    public void onVehicleMove(final org.bukkit.event.vehicle.VehicleMoveEvent event) {
        final Location from = event.getFrom();
        final Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        if (!configManager.getBoolean("territory.capital-law.enabled", true)
                || !configManager.getBoolean("territory.capital-law.wanted-ban", true)
                || !isNeutralCapital(to) || isNeutralCapital(from)) {
            return;
        }
        for (final org.bukkit.entity.Entity passenger : event.getVehicle().getPassengers()) {
            if (passenger instanceof Player player && isWanted(player) && !hasMenlevel(player)) {
                event.getVehicle().eject();
                player.teleportAsync(from);
                player.sendActionBar(messageManager.getMessage("capital-law-wanted",
                        "<red>⛨ Az őrség felismert — körözötteknek tilos a belépés Caldesterába. <gray>(Egy menlevél… segíthetne.)</gray></red>"));
            }
        }
    }

    /** Bejelentkezéskor is érvényt szerzünk a fegyvertilalomnak, ha a játékos a zónában áll. */
    @EventHandler
    public void onJoin(final org.bukkit.event.player.PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (configManager.getBoolean("territory.capital-law.enabled", true)
                && isNeutralCapital(player.getLocation())) {
            enforceWeaponBan(player);
        }
    }

    /** Láda-zárás után: shift-kattal az aktív slotba került fegyvert is elrakatja az őrség. */
    @EventHandler
    public void onInventoryClose(final org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player
                && configManager.getBoolean("territory.capital-law.enabled", true)
                && isNeutralCapital(player.getLocation())) {
            player.getScheduler().run(plugin, task -> enforceWeaponBan(player), null);
        }
    }

    private void handleBorderCross(final PlayerMoveEvent event) {
        final Location from = event.getFrom();
        final Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        if (!configManager.getBoolean("territory.capital-law.enabled", true) || !isNeutralCapital(to)) {
            return;
        }
        final Player player = event.getPlayer();

        // Körözött-kapu: csak a HATÁRÁTLÉPÉSKOR ellenőrzünk (bent lévőt nem rángatunk).
        if (configManager.getBoolean("territory.capital-law.wanted-ban", true)
                && !isNeutralCapital(from) && isWanted(player) && !hasMenlevel(player)) {
            event.setCancelled(true);
            player.sendActionBar(messageManager.getMessage("capital-law-wanted",
                    "<red>⛨ Az őrség felismert — körözötteknek tilos a belépés Caldesterába. <gray>(Egy menlevél… segíthetne.)</gray></red>"));
            return;
        }

        enforceWeaponBan(player);
    }

    @EventHandler
    public void onHeldChange(final PlayerItemHeldEvent event) {
        final Player player = event.getPlayer();
        if (configManager.getBoolean("territory.capital-law.enabled", true)
                && isNeutralCapital(player.getLocation())) {
            // A slot-váltás UTÁNI kéz-állapotot a következő tick látja — a játékos saját
            // entity-schedulerén ellenőrzünk (Folia-safe).
            player.getScheduler().run(plugin, task -> enforceWeaponBan(player), null);
        }
    }

    /** Nyílt fegyver a kézben → az őrség elrakatja (a kéz kiürül, a fegyver az inventoryba kerül). */
    private void enforceWeaponBan(final Player player) {
        if (!configManager.getBoolean("territory.capital-law.weapon-ban", true)) {
            return;
        }
        final ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir() || !isWeapon(hand.getType())) {
            return;
        }
        player.getInventory().setItemInMainHand(null);
        player.getInventory().addItem(hand).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        player.sendActionBar(messageManager.getMessage("capital-law-weapon",
                "<red>⛨ Caldesterában tilos a nyílt fegyver — az őrség elrakatta.</red>"));
    }

    /** Fegyver-anyagok (a Sétapálca BOT — a szabály szándékosan nem látja: ez a kiskapu). */
    private static boolean isWeapon(final Material material) {
        final String name = material.name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE")
                || name.equals("TRIDENT") || name.equals("BOW") || name.equals("CROSSBOW") || name.equals("MACE");
    }

    private boolean isNeutralCapital(final Location location) {
        final Territory zone = territoryManager.getTerritoryAt(location);
        return zone != null && zone.type() == TerritoryType.CAPITAL && zone.faction() == FactionType.NEUTRAL;
    }

    /** Körözött: elérte a vérdíj-küszöböt (factions.sins.bounty.min-sins). */
    private boolean isWanted(final Player player) {
        if (!configManager.getBoolean("factions.sins.bounty.enabled", true)) {
            return false;
        }
        return sinManager.getSinCount(player) >= Math.max(1, configManager.getInt("factions.sins.bounty.min-sins", 3));
    }

    /** Van-e nála Hamisított Menlevél (signature-tagelt feketepiac-áru). */
    private boolean hasMenlevel(final Player player) {
        for (final ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.hasItemMeta()
                    && "hamisitott_menlevel".equals(item.getItemMeta().getPersistentDataContainer()
                            .get(SIGNATURE_KEY, PersistentDataType.STRING))) {
                return true;
            }
        }
        return false;
    }

    /** A shop-signature azonosítók (a feketepiac árui). */
    public static final String SETAPALCA = "botera_setapalca";
    public static final String MENLEVEL = "hamisitott_menlevel";
}
