package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.data.TerritoryType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.DungeonLootService;
import hu.taliann.icesmp.managers.TerritoryManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A kazamata-loot réteg eseményei: regisztrált kincsesláda nyitása (fejenkénti
 * virtuális zsákmány), mini-boss ébresztés zónába lépéskor, boss-halál loot,
 * és bónusz mob-drop a kazamatán belül.
 */
public final class DungeonLootListener implements Listener {

    private final DungeonLootService lootService;
    private final TerritoryManager territoryManager;
    private final ConfigManager configManager;
    private final hu.taliann.icesmp.managers.AfkManager afkManager;

    public DungeonLootListener(final hu.taliann.icesmp.managers.AfkManager afkManager,
                               final DungeonLootService lootService, final TerritoryManager territoryManager,
                               final ConfigManager configManager) {
        this.lootService = lootService;
        this.territoryManager = territoryManager;
        this.configManager = configManager;
        this.afkManager = afkManager;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onChestOpen(final PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        final Material type = event.getClickedBlock().getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST && type != Material.BARREL) {
            return;
        }
        if (!lootService.isRegisteredChest(event.getClickedBlock().getLocation())) {
            return;
        }
        // A vanília konténer sosem nyílik meg: a loot fejenkénti és virtuális.
        event.setCancelled(true);
        lootService.claimChest(event.getPlayer(), event.getClickedBlock().getLocation());
    }

    /** Kazamata-határ átlépésekor ébred a mini-boss (blokk-váltásra szűrve — olcsó). */
    @EventHandler(ignoreCancelled = true)
    public void onMove(final PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        final Territory to = territoryManager.getTerritoryAt(event.getTo());
        if (to == null || to.type() != TerritoryType.DUNGEON) {
            return;
        }
        final Territory from = territoryManager.getTerritoryAt(event.getFrom());
        if (from != null && from.id().equals(to.id())) {
            return;
        }
        lootService.maybeSpawnBoss(to.id());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(final EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        // Mini-boss: saját tábla, respawn-óra.
        if (lootService.handleBossDeath(event.getEntity(), event.getDrops())) {
            return;
        }
        // Bónusz mob-drop a kazamatán belül (csak játékos-kill; a kapu mögötti
        // kockázatnak a sima mob-looton felül is legyen hozadéka).
        // A kazamata-lelet FAUCET-tier (új értéket termel), ezért a közös előszűrőn megy át:
        // survival-kapu, AFK-fék, spawner- és minion-kizárás — enélkül ez az ág kiskapu volt.
        final Player killer = hu.taliann.icesmp.utils.MobKillUtil.eligibleKiller(event.getEntity(),
                hu.taliann.icesmp.utils.MobKillUtil.RewardKind.FAUCET, configManager, afkManager);
        if (killer == null) {
            return;
        }
        final Territory zone = territoryManager.getTerritoryAt(event.getEntity().getLocation());
        if (zone == null || zone.type() != TerritoryType.DUNGEON) {
            return;
        }
        final double chance = configManager.getDouble("dungeon.mob-loot.chance", 0.08D);
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        event.getDrops().addAll(lootService.rollTable(
                configManager.getString("dungeon.mob-loot.table", "kazamata"), 1));
    }
}
