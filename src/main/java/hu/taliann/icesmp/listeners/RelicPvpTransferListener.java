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
     * (bárki felkapná és a tulajdonos számára örökre elveszne — audit-hiba), hanem
     * respawnkor visszakerül a gazdájához. Kilépés respawn előtt = a stash elvész (ritka;
     * ugyanaz a kompromisszum, mint a Lélekkapocs-védelemnél).
     */
    private final Map<java.util.UUID, List<ItemStack>> keptRelics = new java.util.concurrent.ConcurrentHashMap<>();

    @EventHandler
    public void onPlayerDeath(final PlayerDeathEvent event) {
        final Player victim = event.getEntity();

        // 1) Passzív relikviák megtartása (minden halálnál, killertől függetlenül).
        if (configManager.getBoolean("relics.keep-passive-on-death", true)) {
            final List<ItemStack> kept = new ArrayList<>();
            event.getDrops().removeIf(drop -> {
                final RelicDefinition definition = relicManager.identify(drop);
                if (definition == null || relicManager.isWeaponRelic(definition.id())) {
                    return false;
                }
                kept.add(drop);
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
        }
        event.getPlayer().sendMessage(messageManager.getMessage(
                "relic.death-kept",
                "<gold>✦ A relikviád hű maradt hozzád — a halál sem választott el tőle.</gold>"));
    }

    @EventHandler
    public void onQuit(final org.bukkit.event.player.PlayerQuitEvent event) {
        keptRelics.remove(event.getPlayer().getUniqueId());
    }
}
