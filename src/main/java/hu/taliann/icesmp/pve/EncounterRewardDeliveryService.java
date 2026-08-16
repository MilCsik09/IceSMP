package hu.taliann.icesmp.pve;

import hu.taliann.icesmp.items.UniqueMaterialFactory;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileOperationStore;
import hu.taliann.icesmp.playerprofile.domain.PlayerProfileOperation;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/** PlayerProfile receipt + playerdata witness boundary for physical personal encounter rewards. */
public final class EncounterRewardDeliveryService implements Listener {
    public static final String OPERATION_TYPE = "encounter-personal-reward";
    public static final String ELIGIBILITY_TYPE = "encounter-reward-eligibility";

    private final JavaPlugin plugin;
    private final UniqueMaterialFactory materials;
    private final MessageManager messages;
    private final PlayerProfileOperationStore operations = new PlayerProfileOperationStore();
    private final NamespacedKey receiptKey;

    public EncounterRewardDeliveryService(final JavaPlugin plugin,
                                          final UniqueMaterialFactory materials,
                                          final MessageManager messages) {
        this.plugin = plugin;
        this.materials = materials;
        this.messages = messages;
        this.receiptKey = new NamespacedKey(plugin, "encounter_reward_receipt");
    }

    public void queue(final Player player, final UUID encounterId,
                      final String materialId, final int amount) {
        if (player == null || encounterId == null || materialId == null
                || materialId.isBlank() || amount < 1 || amount > 64) return;
        queueDelivery(player, encounterId, materialId, amount);
    }

    public void reserveEligibility(final UUID playerId, final UUID encounterId,
                                   final String materialId, final int amount) {
        if (playerId == null || encounterId == null || materialId == null
                || materialId.isBlank() || amount < 1 || amount > 64) return;
        operations.prepare(playerId, eligibilityId(encounterId), ELIGIBILITY_TYPE,
                        fingerprint(materialId, amount), Map.of("material", materialId,
                                "amount", Integer.toString(amount),
                                "encounter", encounterId.toString()))
                .exceptionally(failure -> {
                    plugin.getLogger().warning("Encounter eligibility reservation failed for "
                            + playerId + '/' + encounterId + ": " + failure.getMessage());
                    return null;
                });
    }

    public void activate(final UUID playerId, final UUID encounterId,
                         final String materialId, final int amount) {
        if (playerId == null || encounterId == null) return;
        operations.prepare(playerId, eligibilityId(encounterId), ELIGIBILITY_TYPE,
                        fingerprint(materialId, amount), Map.of("material", materialId,
                                "amount", Integer.toString(amount),
                                "encounter", encounterId.toString()))
                .thenCompose(operation -> operations.commit(playerId,
                        eligibilityId(encounterId), ELIGIBILITY_TYPE,
                        fingerprint(materialId, amount)))
                .whenComplete((operation, failure) -> {
                    if (failure != null) {
                        plugin.getLogger().warning("Encounter eligibility activation failed for "
                                + playerId + '/' + encounterId + ": " + failure.getMessage());
                        return;
                    }
                    final Player player = plugin.getServer().getPlayer(playerId);
                    if (player != null) player.getScheduler().run(plugin,
                            task -> queueDelivery(player, encounterId, materialId, amount), null);
                });
    }

    public void reject(final UUID playerId, final UUID encounterId,
                       final String materialId, final int amount) {
        operations.prepare(playerId, eligibilityId(encounterId), ELIGIBILITY_TYPE,
                        fingerprint(materialId, amount), Map.of("material", materialId,
                                "amount", Integer.toString(amount),
                                "encounter", encounterId.toString()))
                .thenCompose(operation -> operations.rollback(playerId,
                        eligibilityId(encounterId), ELIGIBILITY_TYPE,
                        fingerprint(materialId, amount)))
                .exceptionally(failure -> null);
    }

    private void queueDelivery(final Player player, final UUID encounterId,
                               final String materialId, final int amount) {
        final String operationId = "encounter-delivery:" + encounterId;
        final String fingerprint = materialId + ':' + amount;
        operations.prepare(player.getUniqueId(), operationId, OPERATION_TYPE, fingerprint,
                        Map.of("material", materialId, "amount", Integer.toString(amount),
                                "encounter", encounterId.toString()))
                .whenComplete((operation, failure) -> {
                    if (failure != null) {
                        plugin.getLogger().severe("Encounter reward reservation failed for "
                                + player.getUniqueId() + '/' + encounterId + ": " + failure.getMessage());
                        return;
                    }
                    player.getScheduler().run(plugin,
                            task -> deliver(player, operation), null);
                });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        // Profile v2 login publication precedes normal gameplay, but recovery yields one second
        // so a slow repository load never races this read.
        player.getScheduler().runDelayed(plugin, task -> recover(player), null, 20L);
    }

    public void recover(final Player player) {
        if (player == null || !player.isOnline()) return;
        try {
            cleanupCommittedMarkers(player);
            // Boss entities are transient and do not survive a restart. A candidate that
            // never became COMMITTED is exact-before, so abort it rather than leaking one
            // recoverable receipt per interrupted encounter. A live boss may reserve it again.
            for (final PlayerProfileOperation candidate : operations.prepared(
                    player.getUniqueId(), ELIGIBILITY_TYPE)) {
                operations.rollback(player.getUniqueId(), candidate.operationId(),
                        candidate.type(), candidate.fingerprint())
                        .exceptionally(failure -> {
                            plugin.getLogger().warning("Encounter candidate recovery failed for "
                                    + player.getUniqueId() + '/' + candidate.operationId()
                                    + ": " + failure.getMessage());
                            return null;
                        });
            }
            for (final PlayerProfileOperation eligibility : operations.byTypeAndStatus(
                    player.getUniqueId(), ELIGIBILITY_TYPE,
                    PlayerProfileOperation.Status.COMMITTED)) {
                final UUID encounterId = UUID.fromString(
                        eligibility.metadata().get("encounter"));
                final int amount = Integer.parseInt(
                        eligibility.metadata().getOrDefault("amount", "0"));
                queueDelivery(player, encounterId,
                        eligibility.metadata().get("material"), amount);
            }
            for (final PlayerProfileOperation operation
                    : operations.prepared(player.getUniqueId(), OPERATION_TYPE)) {
                deliver(player, operation);
            }
        } catch (final RuntimeException notReady) {
            plugin.getLogger().warning("Encounter reward recovery deferred for "
                    + player.getUniqueId() + ": " + notReady.getMessage());
        }
    }

    private void deliver(final Player player, final PlayerProfileOperation operation) {
        if (!player.isOnline() || operation.status() == PlayerProfileOperation.Status.COMMITTED) return;
        if (hasReceipt(player, operation.operationId())) {
            commit(player, operation);
            return;
        }
        final String materialId = operation.metadata().get("material");
        final int amount;
        try {
            amount = Integer.parseInt(operation.metadata().getOrDefault("amount", "0"));
        } catch (final NumberFormatException malformed) {
            plugin.getLogger().severe("Malformed encounter reward operation: " + operation.operationId());
            return;
        }
        final ItemStack reward = materials.create(materialId, amount);
        if (reward == null) {
            plugin.getLogger().severe("Unknown encounter reward material: " + materialId);
            return;
        }
        final var meta = reward.getItemMeta();
        meta.getPersistentDataContainer().set(receiptKey, PersistentDataType.STRING,
                operation.operationId());
        reward.setItemMeta(meta);
        if (!canFit(player, reward)) {
            player.sendActionBar(messages.get("encounter-reward-inventory-full",
                    "&eA személyes bossjutalmad függőben maradt; szabadíts fel egy inventoryhelyet."));
            return;
        }
        final ItemStack[] before = cloneContents(player.getInventory().getStorageContents());
        try {
            if (!player.getInventory().addItem(reward).isEmpty()) {
                player.getInventory().setStorageContents(before);
                return;
            }
            player.saveData();
        } catch (final RuntimeException persistenceFailure) {
            player.getInventory().setStorageContents(before);
            try { player.saveData(); }
            catch (final RuntimeException rollbackFailure) {
                persistenceFailure.addSuppressed(rollbackFailure);
            }
            plugin.getLogger().severe("Encounter reward playerdata save failed for "
                    + player.getUniqueId() + '/' + operation.operationId() + ": "
                    + persistenceFailure.getMessage());
            return;
        }
        commit(player, operation);
    }

    private void commit(final Player player, final PlayerProfileOperation operation) {
        operations.commit(player.getUniqueId(), operation.operationId(), OPERATION_TYPE,
                        operation.fingerprint())
                .whenComplete((committed, failure) -> player.getScheduler().run(plugin, task -> {
                    if (failure != null) {
                        plugin.getLogger().warning("Encounter reward receipt stays PREPARED: "
                                + operation.operationId() + ": " + failure.getMessage());
                        return;
                    }
                    stripReceipt(player, operation.operationId());
                    try { player.saveData(); }
                    catch (final RuntimeException saveFailure) {
                        plugin.getLogger().warning("Committed encounter reward marker cleanup deferred: "
                                + operation.operationId());
                    }
                }, null));
    }

    private void cleanupCommittedMarkers(final Player player) {
        for (final ItemStack item : player.getInventory().getStorageContents()) {
            final String receipt = receipt(item);
            if (receipt == null) continue;
            final var operation = operations.read(player.getUniqueId(), receipt).orElse(null);
            if (operation != null && operation.status() == PlayerProfileOperation.Status.PREPARED) {
                commit(player, operation);
            } else if (operation != null && operation.status() == PlayerProfileOperation.Status.COMMITTED) {
                stripReceipt(player, receipt);
            }
        }
    }

    private boolean hasReceipt(final Player player, final String operationId) {
        return Arrays.stream(player.getInventory().getStorageContents())
                .anyMatch(item -> operationId.equals(receipt(item)));
    }

    private String receipt(final ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(receiptKey, PersistentDataType.STRING);
    }

    private void stripReceipt(final Player player, final String operationId) {
        final ItemStack[] contents = player.getInventory().getStorageContents();
        boolean changed = false;
        for (final ItemStack item : contents) {
            if (!operationId.equals(receipt(item))) continue;
            final var meta = item.getItemMeta();
            meta.getPersistentDataContainer().remove(receiptKey);
            item.setItemMeta(meta);
            changed = true;
        }
        if (changed) player.getInventory().setStorageContents(contents);
    }

    private static boolean canFit(final Player player, final ItemStack incoming) {
        int remaining = incoming.getAmount();
        final int maximum = Math.max(1, incoming.getMaxStackSize());
        for (final ItemStack existing : player.getInventory().getStorageContents()) {
            if (existing == null || existing.getType().isAir()) remaining -= maximum;
            else if (existing.isSimilar(incoming)) {
                remaining -= Math.max(0, maximum - existing.getAmount());
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    private static ItemStack[] cloneContents(final ItemStack[] source) {
        final ItemStack[] clone = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            clone[index] = source[index] == null ? null : source[index].clone();
        }
        return clone;
    }

    private static String eligibilityId(final UUID encounterId) {
        return "encounter-eligible:" + encounterId;
    }

    private static String fingerprint(final String materialId, final int amount) {
        return materialId + ':' + amount;
    }
}
