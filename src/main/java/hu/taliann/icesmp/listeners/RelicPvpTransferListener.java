package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.RelicManager;
import hu.taliann.icesmp.playerprofile.application.DeathEscrowDeliveryPlan;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileDeathEscrowStore;
import hu.taliann.icesmp.relics.RelicDefinition;
import hu.taliann.icesmp.relics.RelicOwnership;
import hu.taliann.icesmp.relics.RelicTransferExpectation;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.NamespacedKey;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** PvP weapon-relic transfer and Profile v2-backed passive-relic death escrow. */
public final class RelicPvpTransferListener implements Listener {

    private final JavaPlugin plugin;
    private final RelicManager relicManager;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final PlayerProfileDeathEscrowStore escrowStore = new PlayerProfileDeathEscrowStore();
    private final NamespacedKey relicOwnerKey;
    private final NamespacedKey escrowDeliveryKey;
    private final Set<UUID> deliveries = ConcurrentHashMap.newKeySet();
    private final Set<UUID> replayingFatalDamage = ConcurrentHashMap.newKeySet();
    private final Map<UUID, CompletionStage<?>> deposits = new ConcurrentHashMap<>();
    private final Map<UUID, PreparedDeath> preparedDeaths = new ConcurrentHashMap<>();

    private record PreparedDeath(long sessionGeneration, String receiptId) { }

    public RelicPvpTransferListener(final JavaPlugin plugin, final RelicManager relicManager,
                                    final ConfigManager configManager,
                                    final MessageManager messageManager) {
        this.plugin = plugin;
        this.relicManager = relicManager;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.relicOwnerKey = new NamespacedKey(plugin, "relic_owner");
        this.escrowDeliveryKey = new NamespacedKey(plugin, "death_escrow_delivery");
    }

    @EventHandler
    public void onPlayerDeath(final PlayerDeathEvent event) {
        final Player victim = event.getEntity();
        final String mode = configManager.getString("relics.passive-death.mode", "reclaim")
                .toLowerCase(Locale.ROOT);
        if ("reclaim".equals(mode)) {
            reclaimPassiveRelics(event, victim);
        } else if ("keep".equals(mode)) {
            removeDurablyEscrowedDrops(event, victim);
        }
        transferWeaponRelics(event, victim);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethalDamage(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)
                || event.getFinalDamage() < victim.getHealth()
                || replayingFatalDamage.contains(victim.getUniqueId())
                || !"keep".equals(configManager.getString(
                        "relics.passive-death.mode", "reclaim").toLowerCase(Locale.ROOT))) {
            return;
        }
        final UUID playerId = victim.getUniqueId();
        if (deposits.containsKey(playerId)) {
            event.setCancelled(true);
            return;
        }
        final var session = hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority
                .current().service().currentSessionGeneration(playerId);
        if (session.isEmpty()) return;
        final long now = System.currentTimeMillis();
        final String receiptId = "relic-death:" + now + ':' + UUID.randomUUID();
        final List<String> encoded = markPassiveRelics(victim, receiptId);
        if (encoded.isEmpty()) return;

        event.setCancelled(true);
        final DamageSource damageSource = event.getDamageSource();
        final CompletionStage<?> deposit = escrowStore.depositForSession(
                playerId, session.getAsLong(), receiptId, encoded, now);
        deposits.put(playerId, deposit);
        deposit.whenComplete((result, failure) -> victim.getScheduler().run(plugin, task -> {
            deposits.remove(playerId, deposit);
            if (!current(victim, session.getAsLong())) return;
            if (failure == null) {
                preparedDeaths.put(playerId,
                        new PreparedDeath(session.getAsLong(), receiptId));
            } else {
                plugin.getLogger().warning("A relikvia halál-escrow mentése meghiúsult; "
                        + "a normál dropút folytatódik: " + rootMessage(failure));
                clearDeliveryMarkersNow(victim, receiptId);
            }
            replayFatalDamage(victim, damageSource);
            if (!victim.isDead()) {
                preparedDeaths.remove(playerId);
                clearDeliveryMarkersNow(victim, receiptId);
                if (failure == null) {
                    escrowStore.settle(playerId, receiptId, System.currentTimeMillis())
                            .exceptionally(settleFailure -> {
                                plugin.getLogger().warning("A megszakadt relikvia-halál escrow "
                                        + "lezárása meghiúsult: " + rootMessage(settleFailure));
                                return null;
                            });
                }
            }
        }, () -> deposits.remove(playerId, deposit)));
    }

    private void reclaimPassiveRelics(final PlayerDeathEvent event, final Player victim) {
        event.getDrops().removeIf(drop -> {
            final RelicDefinition definition = relicManager.identify(drop);
            if (definition == null || relicManager.isWeaponRelic(definition.id())) return false;
            if (!relicManager.markLost(definition.id(), victim.getUniqueId())) return true;
            victim.sendMessage(messageManager.getMessage(
                    "relic.death-lost",
                    "<dark_purple>✦ A(z) <white>{relic}</white> köddé vált a halálodban — a kötés él: idézd újra az oltárnál, mielőtt végleg elhagyna ({days} nap).</dark_purple>",
                    Map.of("relic", definition.displayName(), "days", String.valueOf(Math.max(
                            0L, configManager.getLong("relics.inactivity.lost-expiry-days", 3L))))));
            return true;
        });
    }

    private List<String> markPassiveRelics(final Player victim, final String receiptId) {
        final List<String> encoded = new ArrayList<>();
        int index = 0;
        for (int slot = 0; slot < victim.getInventory().getSize(); slot++) {
            final ItemStack item = victim.getInventory().getItem(slot);
            if (item == null) continue;
            final RelicDefinition definition = relicManager.identify(item);
            if (definition == null || relicManager.isWeaponRelic(definition.id())) continue;
            final var meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(escrowDeliveryKey, PersistentDataType.STRING,
                    DeathEscrowDeliveryPlan.deliveryId(receiptId, index++));
            item.setItemMeta(meta);
            victim.getInventory().setItem(slot, item);
            encoded.add(encode(item));
        }
        return List.copyOf(encoded);
    }

    private void removeDurablyEscrowedDrops(final PlayerDeathEvent event, final Player victim) {
        final PreparedDeath prepared = preparedDeaths.remove(victim.getUniqueId());
        if (prepared == null || !current(victim, prepared.sessionGeneration())) return;
        event.getDrops().removeIf(drop -> hasDeliveryMarker(drop, prepared.receiptId()));
    }

    private void replayFatalDamage(final Player victim, final DamageSource damageSource) {
        final UUID playerId = victim.getUniqueId();
        if (!replayingFatalDamage.add(playerId)) return;
        try {
            victim.damage(Math.max(2_048.0D,
                    victim.getHealth() + victim.getAbsorptionAmount() + 128.0D), damageSource);
        } finally {
            replayingFatalDamage.remove(playerId);
        }
    }

    private void transferWeaponRelics(final PlayerDeathEvent event, final Player victim) {
        if (!configManager.getBoolean("relics.pvp-transfer.enabled", true)) return;
        final Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) return;
        final List<String> claimedNames = new ArrayList<>();
        for (final ItemStack drop : event.getDrops()) {
            final RelicDefinition definition = relicManager.identify(drop);
            if (definition == null || !relicManager.isWeaponRelic(definition.id())) continue;
            final String itemOwner = drop.hasItemMeta()
                    ? drop.getItemMeta().getPersistentDataContainer()
                            .get(relicOwnerKey, PersistentDataType.STRING)
                    : null;
            if (!victim.getUniqueId().toString().equals(itemOwner)) continue;
            RelicTransferExpectation.withExpectedOwner(victim.getUniqueId(), () ->
                    relicManager.transferOwnership(definition.id(), drop, killer));
            final RelicOwnership transferred = relicManager.getOwnership(definition.id());
            final String rewrittenOwner = drop.hasItemMeta()
                    ? drop.getItemMeta().getPersistentDataContainer()
                            .get(relicOwnerKey, PersistentDataType.STRING)
                    : null;
            if (transferred == null || !killer.getUniqueId().equals(transferred.owner())
                    || !killer.getUniqueId().toString().equals(rewrittenOwner)) continue;
            claimedNames.add(definition.displayName());
            victim.sendMessage(messageManager.getMessage(
                    "relic.pvp-lost",
                    "<red>A(z) <white>{relic}</white> elhagyott — legyőződ kezébe került.</red>",
                    Map.of("relic", definition.displayName())));
        }
        if (claimedNames.isEmpty()) return;
        killer.getScheduler().run(plugin, task -> {
            for (final String relicName : claimedNames) {
                killer.sendMessage(messageManager.getMessage(
                        "relic.pvp-claimed",
                        "<gold>⚔ A(z) <white>{relic}</white> új gazdát választott: mostantól téged szolgál!</gold>",
                        Map.of("relic", relicName)));
            }
        }, null);
    }

    @EventHandler
    public void onRespawn(final PlayerRespawnEvent event) {
        recoverEscrow(event.getPlayer());
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        recoverEscrow(event.getPlayer());
    }

    private void recoverEscrow(final Player player) {
        recoverEscrow(player, 0);
    }

    private void recoverEscrow(final Player player, final int readinessAttempt) {
        final UUID playerId = player.getUniqueId();
        final CompletionStage<?> deposit = deposits.get(playerId);
        if (deposit != null) {
            deposit.whenComplete((ignored, failure) -> recoverEscrow(player));
            return;
        }
        final var session = hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority
                .current().service().currentSessionGeneration(playerId);
        if (session.isEmpty()) {
            if (readinessAttempt < 20) {
                player.getScheduler().runDelayed(plugin,
                        task -> recoverEscrow(player, readinessAttempt + 1), null, 10L);
            } else {
                plugin.getLogger().warning("A relikvia halál-escrow recovery kihagyva: "
                        + "a PlayerProfile session nem vált készre időben (" + playerId + ").");
            }
            return;
        }
        if (!deliveries.add(playerId)) return;
        escrowStore.pending(playerId)
                .thenCompose(batches -> deliverBatches(player, session.getAsLong(), batches, 0))
                .whenComplete((delivered, failure) -> {
                    deliveries.remove(playerId);
                    if (failure != null) {
                        plugin.getLogger().warning("A relikvia halál-escrow kézbesítése "
                                + "meghiúsult: " + rootMessage(failure));
                    } else if (Boolean.TRUE.equals(delivered)) {
                        player.getScheduler().run(plugin, task -> {
                            if (current(player, session.getAsLong())) player.sendMessage(
                                    messageManager.getMessage("relic.death-kept",
                                            "<gold>✦ A relikviád hű maradt hozzád — a halál sem választott el tőle.</gold>"));
                        }, null);
                    }
                });
    }

    private CompletionStage<Boolean> deliverBatches(
            final Player player, final long sessionGeneration,
            final List<PlayerProfileDeathEscrowStore.Batch> batches,
            final int index) {
        if (index >= batches.size()) {
            if (batches.isEmpty()) clearDeliveryMarkers(player, sessionGeneration, null);
            return CompletableFuture.completedFuture(!batches.isEmpty());
        }
        return deliverBatch(player, sessionGeneration, batches.get(index)).thenCompose(delivered ->
                delivered ? deliverBatches(player, sessionGeneration, batches, index + 1)
                        : CompletableFuture.completedFuture(false));
    }

    private CompletionStage<Boolean> deliverBatch(
            final Player player, final long sessionGeneration,
            final PlayerProfileDeathEscrowStore.Batch batch) {
        final CompletableFuture<Boolean> result = new CompletableFuture<>();
        player.getScheduler().run(plugin, task -> {
            if (!current(player, sessionGeneration)) {
                result.complete(false);
                return;
            }
            final Set<String> present = deliveryMarkers(player);
            final List<DeathEscrowDeliveryPlan.Item> missing =
                    DeathEscrowDeliveryPlan.missing(batch, present);
            if (freeStorageSlots(player) < missing.size()) {
                player.sendMessage(messageManager.getMessage("relic.death-escrow-full",
                        "<yellow>A megőrzött relikviád kézbesítéséhez üríts helyet a tárgytáradban, majd lépj vissza.</yellow>"));
                result.complete(false);
                return;
            }
            for (final DeathEscrowDeliveryPlan.Item delivery : missing) {
                final ItemStack item = decode(delivery.encodedItem());
                final var meta = item.getItemMeta();
                meta.getPersistentDataContainer().set(
                        escrowDeliveryKey, PersistentDataType.STRING, delivery.deliveryId());
                item.setItemMeta(meta);
                if (!player.getInventory().addItem(item).isEmpty()) {
                    result.completeExceptionally(
                            new IllegalStateException("Escrow inventory capacity changed during delivery"));
                    return;
                }
            }
            escrowStore.settle(player.getUniqueId(), batch.receiptId(), System.currentTimeMillis())
                    .whenComplete((status, failure) -> {
                        if (failure != null) result.completeExceptionally(failure);
                        else {
                            clearDeliveryMarkers(player, sessionGeneration, batch.receiptId());
                            result.complete(true);
                        }
                    });
        }, () -> result.complete(false));
        return result;
    }

    private Set<String> deliveryMarkers(final Player player) {
        final Set<String> markers = ConcurrentHashMap.newKeySet();
        for (final ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            final String marker = item.getItemMeta().getPersistentDataContainer()
                    .get(escrowDeliveryKey, PersistentDataType.STRING);
            if (marker != null) markers.add(marker);
        }
        return Set.copyOf(markers);
    }

    private void clearDeliveryMarkers(final Player player, final long sessionGeneration,
                                      final String receiptId) {
        player.getScheduler().run(plugin, task -> {
            if (!current(player, sessionGeneration)) return;
            clearDeliveryMarkersNow(player, receiptId);
        }, null);
    }

    private void clearDeliveryMarkersNow(final Player player, final String receiptId) {
        for (final ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            final var meta = item.getItemMeta();
            final String marker = meta.getPersistentDataContainer()
                    .get(escrowDeliveryKey, PersistentDataType.STRING);
            if (marker == null || (receiptId == null
                    ? !marker.startsWith("relic-death:")
                    : !marker.startsWith(receiptId + ':'))) continue;
            meta.getPersistentDataContainer().remove(escrowDeliveryKey);
            item.setItemMeta(meta);
        }
    }

    private boolean hasDeliveryMarker(final ItemStack item, final String receiptId) {
        if (item == null || !item.hasItemMeta()) return false;
        final String marker = item.getItemMeta().getPersistentDataContainer()
                .get(escrowDeliveryKey, PersistentDataType.STRING);
        return marker != null && marker.startsWith(receiptId + ':');
    }

    private static boolean current(final Player player, final long sessionGeneration) {
        return player.isOnline()
                && hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority.current()
                .service().isCurrentSession(player.getUniqueId(), sessionGeneration);
    }

    private static int freeStorageSlots(final Player player) {
        int free = 0;
        for (final ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) free++;
        }
        return free;
    }

    private static String encode(final ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    private static ItemStack decode(final String encoded) {
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
    }

    private static String rootMessage(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
