package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileDeathEscrowStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Lélekkapocs (Ability Catalyst) cannot be thrown away — the Tree's gift stays
 * with its bearer (lore: docs/LORE.md VI). Q-drop is cancelled, and on death the
 * catalyst is lifted out of the drops into a durable PlayerProfile escrow, so a
 * crash, quit or kick between death and respawn can no longer lose it: the next
 * respawn or join claims the escrow exactly once and hands the catalyst back.
 */
public final class CatalystProtectionListener implements Listener {

    private final JavaPlugin plugin;
    private final CatalystItemFactory catalystItemFactory;
    private final MessageManager messageManager;
    private final PlayerProfileDeathEscrowStore escrowStore = new PlayerProfileDeathEscrowStore();

    /**
     * The respawn claim must run after the death deposit committed, otherwise a fast respawn
     * click would read the pre-deposit escrow and defer the hand-back to the next join.
     */
    private final Map<UUID, CompletionStage<?>> pendingDeposits = new ConcurrentHashMap<>();

    public CatalystProtectionListener(final JavaPlugin plugin,
                                      final CatalystItemFactory catalystItemFactory,
                                      final MessageManager messageManager) {
        this.plugin = plugin;
        this.catalystItemFactory = catalystItemFactory;
        this.messageManager = messageManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(final PlayerDropItemEvent event) {
        if (!catalystItemFactory.isCatalyst(event.getItemDrop().getItemStack())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(messageManager.get("catalyst-no-drop",
                "&8A Lélekkapocs nem hagyja el a gazdáját — a Fa ajándéka veled marad."));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(final PlayerDeathEvent event) {
        if (PlayerProfileAuthority.installed().isEmpty()) {
            return;
        }
        final List<ItemStack> kept = new ArrayList<>();
        final Iterator<ItemStack> drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            final ItemStack drop = drops.next();
            if (catalystItemFactory.isCatalyst(drop)) {
                drops.remove();
                kept.add(drop);
            }
        }
        if (kept.isEmpty()) {
            return;
        }
        final Player player = event.getEntity();
        final UUID playerId = player.getUniqueId();
        final Location deathLocation = player.getLocation().clone();
        final List<String> encoded = new ArrayList<>(kept.size());
        for (final ItemStack item : kept) {
            encoded.add(Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        }
        final CompletionStage<?> deposit = escrowStore
                .deposit(playerId, encoded, System.currentTimeMillis())
                .whenComplete((size, failure) -> {
                    if (failure == null) {
                        return;
                    }
                    plugin.getLogger().severe("Lélekkapocs escrow mentése sikertelen ("
                            + playerId + "): " + failure.getMessage());
                    // The items are already lifted out of the drops; without a durable escrow the
                    // only loss-free fallback is dropping them back at the death location.
                    plugin.getServer().getRegionScheduler().run(plugin, deathLocation, task -> {
                        for (final ItemStack item : kept) {
                            deathLocation.getWorld().dropItemNaturally(deathLocation, item);
                        }
                    });
                });
        pendingDeposits.put(playerId, deposit);
    }

    @EventHandler
    public void onRespawn(final PlayerRespawnEvent event) {
        deliverEscrow(event.getPlayer());
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        deliverEscrow(event.getPlayer());
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        pendingDeposits.remove(event.getPlayer().getUniqueId());
    }

    private void deliverEscrow(final Player player) {
        if (PlayerProfileAuthority.installed().isEmpty()) {
            return;
        }
        final UUID playerId = player.getUniqueId();
        final CompletionStage<?> pending = pendingDeposits.remove(playerId);
        (pending == null ? CompletableFuture.completedFuture(null) : pending)
                .exceptionally(ignored -> null)
                .thenCompose(ignored -> escrowStore.claim(playerId))
                .whenComplete((encoded, failure) -> {
                    if (failure != null) {
                        plugin.getLogger().severe("Lélekkapocs escrow kiadása sikertelen ("
                                + playerId + "): " + failure.getMessage());
                        return;
                    }
                    if (encoded == null || encoded.isEmpty()) {
                        return;
                    }
                    player.getScheduler().run(plugin, task -> {
                        if (!player.isOnline()) {
                            // The claim already emptied the durable escrow; re-deposit so the
                            // catalyst survives until the player's next join.
                            redeposit(playerId, encoded);
                            return;
                        }
                        for (final String payload : encoded) {
                            final ItemStack item;
                            try {
                                item = ItemStack.deserializeBytes(Base64.getDecoder().decode(payload));
                            } catch (final RuntimeException corrupt) {
                                plugin.getLogger().severe("Lélekkapocs escrow-elem nem olvasható ("
                                        + playerId + "): " + corrupt.getMessage());
                                continue;
                            }
                            player.getInventory().addItem(item).values().forEach(left ->
                                    player.getWorld().dropItemNaturally(player.getLocation(), left));
                        }
                    }, () -> redeposit(playerId, encoded));
                });
    }

    private void redeposit(final UUID playerId, final List<String> encoded) {
        escrowStore.deposit(playerId, encoded, System.currentTimeMillis())
                .whenComplete((size, failure) -> {
                    if (failure != null) {
                        plugin.getLogger().severe("Lélekkapocs escrow visszatétele sikertelen ("
                                + playerId + "): " + failure.getMessage());
                    }
                });
    }
}
