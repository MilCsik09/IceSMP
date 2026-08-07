package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.RelicManager;
import hu.taliann.icesmp.relics.RelicDefinition;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PvP weapon-relic transfer: when a player is slain, weapon relics among
 * their drops change owner to the killer — passive relics stay protected
 * and keep their owner.
 */
public final class RelicPvpTransferListener implements Listener {

    private final JavaPlugin plugin;
    private final RelicManager relicManager;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    public RelicPvpTransferListener(final JavaPlugin plugin, final RelicManager relicManager,
                                    final ConfigManager configManager,
                                    final MessageManager messageManager) {
        this.plugin = plugin;
        this.relicManager = relicManager;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    /**
     * Halál-stash a passzív relikviákhoz: a nem-fegyver relikvia halálkor NEM esik a földre
     * (bárki felkapná és a tulajdonos számára örökre elveszne), hanem respawnkor visszakerül
     * a gazdájához. A stash csak memóriában él, ezért a halál pillanatában a relikvia
     * TARTÓS „elveszett" jelölést is kap: ha a játékos respawn előtt kilép (vagy a szerver
     * elszáll), a tárgy nem tűnik el végleg — a tulaj újraidézheti az oltárnál. A jelölést a
     * sikeres respawn-kézbesítés törli, különben a visszakapott tárgy MELLÉ is idézhetne egyet.
     */
    private final Map<java.util.UUID, List<ItemStack>> keptRelics = new java.util.concurrent.ConcurrentHashMap<>();

    @EventHandler
    public void onPlayerDeath(final PlayerDeathEvent event) {
        final Player victim = event.getEntity();

        // 1) Passzív relikviák sorsa halálkor (relics.passive-death.mode, élőben olvasva):
        //    reclaim (default) — a tárgy MEGSEMMISÜL, a tulajdon marad: csak a tulaj idézheti
        //                        újra az oltárnál, a rövidített lost-expiry lejártáig;
        //    keep             — a tárgy respawnkor visszakerül a tulajhoz;
        //    drop             — vanília viselkedés (leesik; idegen frakció így sem veheti fel).
        final String mode = configManager.getString("relics.passive-death.mode", "reclaim")
                .toLowerCase(java.util.Locale.ROOT);
        if ("reclaim".equals(mode)) {
            event.getDrops().removeIf(drop -> {
                final RelicDefinition definition = relicManager.identify(drop);
                if (definition == null || relicManager.isWeaponRelic(definition.id())) {
                    return false;
                }
                // Owner-kötött lost: egy stale (nem a központi tulajhoz tartozó) példány
                // gazdájának halála nem jelölheti el másvalaki élő relicét — a stale
                // másolat csendben megsemmisül, üzenet és lost-mutáció nélkül.
                if (!relicManager.markLost(definition.id(), victim.getUniqueId())) {
                    return true;
                }
                victim.sendMessage(messageManager.getMessage(
                        "relic.death-lost",
                        "<dark_purple>✦ A(z) <white>{relic}</white> köddé vált a halálodban — a kötés él: idézd újra az oltárnál, mielőtt végleg elhagyna ({days} nap).</dark_purple>",
                        Map.of("relic", definition.displayName(),
                                "days", String.valueOf(Math.max(0L, configManager.getLong("relics.inactivity.lost-expiry-days", 3L))))));
                return true;
            });
        } else if ("keep".equals(mode)) {
            final List<ItemStack> kept = new ArrayList<>();
            event.getDrops().removeIf(drop -> {
                final RelicDefinition definition = relicManager.identify(drop);
                if (definition == null || relicManager.isWeaponRelic(definition.id())) {
                    return false;
                }
                kept.add(drop);
                relicManager.markLost(definition.id(), victim.getUniqueId());
                return true;
            });
            if (!kept.isEmpty()) {
                keptRelics.put(victim.getUniqueId(), kept);
            }
        }

        // 2) Fegyver-relikviák PvP-átvétele.
        if (!configManager.getBoolean("relics.pvp-transfer.enabled", true)) {
            return;
        }

        final Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        // The drops list and the victim are the death event's own entity (this runs on the victim's
        // region thread), so transferring ownership (only reads killer.getUniqueId()) and messaging
        // the victim are safe here. The killer is a different entity, so collect the claimed names and
        // deliver the killer's messages on the killer's own scheduler (Folia cross-entity rule).
        final List<String> claimedNames = new ArrayList<>();
        for (final ItemStack drop : event.getDrops()) {
            final RelicDefinition definition = relicManager.identify(drop);
            if (definition == null || !relicManager.isWeaponRelic(definition.id())) {
                continue;
            }

            relicManager.transferOwnership(definition.id(), drop, killer);
            claimedNames.add(definition.displayName());
            victim.sendMessage(messageManager.getMessage(
                    "relic.pvp-lost",
                    "<red>A(z) <white>{relic}</white> elhagyott — legyőződ kezébe került.</red>",
                    Map.of("relic", definition.displayName())
            ));
        }

        if (claimedNames.isEmpty()) {
            return;
        }
        killer.getScheduler().run(plugin, task -> {
            for (final String relicName : claimedNames) {
                killer.sendMessage(messageManager.getMessage(
                        "relic.pvp-claimed",
                        "<gold>⚔ A(z) <white>{relic}</white> új gazdát választott: mostantól téged szolgál!</gold>",
                        Map.of("relic", relicName)
                ));
            }
        }, null);
    }

    @EventHandler
    public void onRespawn(final org.bukkit.event.player.PlayerRespawnEvent event) {
        final List<ItemStack> kept = keptRelics.remove(event.getPlayer().getUniqueId());
        if (kept == null) {
            return;
        }
        // A respawn-event a játékos saját régió-szálán fut — az inventory-írás biztonságos.
        for (final ItemStack itemStack : kept) {
            event.getPlayer().getInventory().addItem(itemStack).values()
                    .forEach(left -> event.getPlayer().getWorld()
                            .dropItemNaturally(event.getPlayer().getLocation(), left));
            // A tárgy fizikailag visszakerült: a halálkori „elveszett" jelölést törölni KELL,
            // különben az oltárnál egy második példány is idézhető lenne mellé.
            final RelicDefinition definition = relicManager.identify(itemStack);
            if (definition != null) {
                relicManager.clearLost(definition.id(), event.getPlayer().getUniqueId());
            }
        }
        event.getPlayer().sendMessage(messageManager.getMessage(
                "relic.death-kept",
                "<gold>✦ A relikviád hű maradt hozzád — a halál sem választott el tőle.</gold>"));
    }

    /**
     * Respawn előtti kilépés: a memóriabeli stash elvész, de a halálkor kiadott tartós
     * „elveszett" jelölés életben tartja a tulajdont — a relikvia az oltárnál újraidézhető.
     */
    @EventHandler
    public void onQuit(final org.bukkit.event.player.PlayerQuitEvent event) {
        clearStash(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(final org.bukkit.event.player.PlayerKickEvent event) {
        // PlayerKickEvent does not reliably chain to PlayerQuitEvent — clear here too.
        // MONITOR + ignoreCancelled: cancelelt kicknél a játékos marad, a stash nem törölhető.
        clearStash(event.getPlayer().getUniqueId());
    }

    private void clearStash(final java.util.UUID playerId) {
        final List<ItemStack> kept = keptRelics.remove(playerId);
        if (kept == null || kept.isEmpty()) {
            return;
        }
        for (final ItemStack itemStack : kept) {
            final RelicDefinition definition = relicManager.identify(itemStack);
            if (definition != null) {
                plugin.getLogger().info("Relikvia-stash elvesztve respawn előtti kilépéssel ("
                        + definition.id() + ") — a tulajdon él, az oltárnál újraidézhető.");
            }
        }
    }
}
