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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/** PlayerProfile receipt + playerdata witness boundary for physical personal encounter rewards. */
public final class EncounterRewardDeliveryService implements Listener {
    public static final String OPERATION_TYPE = "encounter-personal-reward";
    public static final String ELIGIBILITY_TYPE = "encounter-reward-eligibility";
    private static final int ABORT_HISTORY_LIMIT = 128;
    private static volatile EncounterRewardDeliveryService activeInstance;

    private record EligibilitySpec(String materialId, int amount) { }

    private final JavaPlugin plugin;
    private final UniqueMaterialFactory materials;
    private final MessageManager messages;
    private final PlayerProfileOperationStore operations = new PlayerProfileOperationStore();
    private final NamespacedKey receiptKey;
    private final Map<UUID, Map<UUID, EligibilitySpec>> preparedEligibility = new ConcurrentHashMap<>();
    private final Set<UUID> abortedEncounters = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedDeque<UUID> abortedOrder = new ConcurrentLinkedDeque<>();

    public EncounterRewardDeliveryService(final JavaPlugin plugin,
                                          final UniqueMaterialFactory materials,
                                          final MessageManager messages) {
        this.plugin = plugin;
        this.materials = materials;
        this.messages = messages;
        this.receiptKey = new NamespacedKey(plugin, "encounter_reward_receipt");
        activeInstance = this;
    }

    /**
     * WorldBossManager owns the single active encounter; this narrow lifecycle hook lets a removal
     * listener reject all still-PREPARED eligibility without creating a second persistence authority.
     */
    public static void abortPreparedEncounter(final UUID encounterId) {
        final EncounterRewardDeliveryService service = activeInstance;
        if (service != null && encounterId != null) service.abortEncounter(encounterId);
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
        preparedEligibility.computeIfAbsent(encounterId, ignored -> new ConcurrentHashMap<>())
                .put(playerId, new EligibilitySpec(materialId, amount));
        operations.prepare(playerId, eligibilityId(encounterId), ELIGIBILITY_TYPE,
                        fingerprint(materialId, amount), Map.of("material", materialId,
                                "amount", Integer.toString(amount),
                                "encounter", encounterId.toString()))
                .whenComplete((operation, failure) -> {
                    if (failure != null) {
                        forgetEligibility(playerId, encounterId);
                        plugin.getLogger().warning("Encounter eligibility reservation failed for "
                                + playerId + '/' + encounterId + ": " + failure.getMessage());
                        return;
                    }
                    if (abortedEncounters.contains(encounterId)) {
                        reject(playerId, encounterId, materialId, amount);
                    }
                });
    }

    public void activate(final UUID playerId, final UUID encounterId,
                         final String materialId, final int amount) {
        if (playerId == null || encounterId == null) return;
        abortedEncounters.remove(encounterId);
        operations.prepare(playerId, eligibilityId(encounterId), ELIGIBILITY_TYPE,
                        fingerprint(materialId, amount), Map.of("material", materialId,
                                "amount", Integer.toString(amount),
                                "encounter", encounterId.toString()))
                .thenCompose(operation -> operations.commit(playerId,
                        eligibilityId(encounterId), ELIGIBILITY_TYPE,
                        fingerprint(materialId, amount)))
                .whenComplete((operation, failure) -> {
                    forgetEligibility(playerId, encounterId);
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
        if (playerId == null || encounterId == null || materialId == null
                || materialId.isBlank() || amount < 1 || amount > 64) return;
        forgetEligibility(playerId, encounterId);
        operations.prepare(playerId, eligibilityId(encounterId), ELIGIBILITY_TYPE,
                        fingerprint(materialId, amount), Map.of("material", materialId,
                                "amount", Integer.toString(amount),
                                "encounter", encounterId.toString()))
                .thenCompose(operation -> operations.rollback(playerId,
                        eligibilityId(encounterId), ELIGIBILITY_TYPE,
                        fingerprint(materialId, amount)))
                .exceptionally(failure -> null);
    }

    private void abortEncounter(final UUID encounterId) {
        rememberAbort(encounterId);
        final Map<UUID, EligibilitySpec> candidates = preparedEligibility.remove(encounterId);
        if (candidates == null || candidates.isEmpty()) return;
        candidates.forEach((playerId, spec) -> operations.rollback(playerId,
                        eligibilityId(encounterId), ELIGIBILITY_TYPE,
                        fingerprint(spec.materialId(), spec.amount()))
                .exceptionally(failure -> {
                    plugin.getLogger().warning("Encounter eligibility abort failed for "
                            + playerId + '/' + encounterId + ": " + failure.getMessage());
                    return null;
                }));
    }

    private void rememberAbort(final UUID encounterId) {
        if (abortedEncounters.add(encounterId)) abortedOrder.addLast(encounterId);
        while (abortedOrder.size() > ABORT_HISTORY_LIMIT) {
            final UUID oldest = abortedOrder.pollFirst();
            if (oldest != null) abortedEncounters.remove(oldest);
        }
    }

    private void forgetEligibility(final UUID playerId, final UUID encounterId) {
        final Map<UUID, EligibilitySpec> candidates = preparedEligibility.get(encounterId);
        if (candidates == null) return;
        candidates.remove(playerId);
        if (candidates.isEmpty()) preparedEligibility.remove(encounterId, candidates);
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
        player.getScheduler().runDelayed(plugin, task -> recover(player), null, 20L);
    }

    public void recover(final Player player) {
        if (player == null || !player.isOnline()) return;
        try {
            cleanupCommittedMarkers(player);
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
        final int witnesses = receiptCount(player, operation.operationId());
        final EncounterRewardRecoveryPolicy.Decision recovery =
                EncounterRewardRecoveryPolicy.decide(
                        EncounterRewardRecoveryPolicy.ReceiptState.PREPARED, witnesses);
        if (recovery == EncounterRewardRecoveryPolicy.Decision.MANUAL_REVIEW) {
            plugin.getLogger().severe("Ambiguous duplicate encounter reward witnesses for "
                    + player.getUniqueId() + '/' + operation.operationId());
            return;
        }
        if (recovery == EncounterRewardRecoveryPolicy.Decision.COMMIT_WITNESS) {
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
                    sendDeliveryReceipt(player, operation);
                    stripReceipt(player, operation.operationId());
                    try { player.saveData(); }
                    catch (final RuntimeException saveFailure) {
                        plugin.getLogger().warning("Committed encounter reward marker cleanup deferred: "
                                + operation.operationId());
                    }
                }, null));
    }

    private void sendDeliveryReceipt(final Player player, final PlayerProfileOperation operation) {
        final String materialId = operation.metadata().get("material");
        final int amount;
        try {
            amount = Math.max(1, Integer.parseInt(operation.metadata().getOrDefault("amount", "1")));
        } catch (final NumberFormatException malformed) {
            return;
        }
        final ItemStack display = materials.create(materialId, amount);
        if (display == null) return;
        final String plainName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(display.effectiveName());
        final String safeName = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .escapeTags(plainName);
        player.sendMessage(messages.getMessage("encounter-reward-delivered",
                "<gold>✦ Személyes világboss-jutalom: <white>{amount}× {item}</white>.</gold>",
                Map.of("amount", Integer.toString(amount), "item", safeName)));
    }

    private void cleanupCommittedMarkers(final Player player) {
        final Set<String> receipts = new LinkedHashSet<>();
        for (final ItemStack item : player.getInventory().getContents()) {
            final String receipt = receipt(item);
            if (receipt != null) receipts.add(receipt);
        }
        for (final String receipt : receipts) {
            final PlayerProfileOperation operation = operations.read(
                    player.getUniqueId(), receipt).orElse(null);
            if (operation == null || !OPERATION_TYPE.equals(operation.type())) continue;
            final EncounterRewardRecoveryPolicy.ReceiptState state = switch (operation.status()) {
                case PREPARED -> EncounterRewardRecoveryPolicy.ReceiptState.PREPARED;
                case COMMITTED -> EncounterRewardRecoveryPolicy.ReceiptState.COMMITTED;
                default -> null;
            };
            if (state == null) continue;
            final EncounterRewardRecoveryPolicy.Decision decision =
                    EncounterRewardRecoveryPolicy.decide(state, receiptCount(player, receipt));
            if (decision == EncounterRewardRecoveryPolicy.Decision.MANUAL_REVIEW) {
                plugin.getLogger().severe("Ambiguous duplicate encounter reward witnesses for "
                        + player.getUniqueId() + '/' + receipt);
            } else if (decision == EncounterRewardRecoveryPolicy.Decision.COMMIT_WITNESS) {
                commit(player, operation);
            } else if (decision == EncounterRewardRecoveryPolicy.Decision.CLEANUP_ONLY) {
                stripReceipt(player, receipt);
            }
        }
    }

    private int receiptCount(final Player player, final String operationId) {
        return (int) Arrays.stream(player.getInventory().getContents())
                .filter(item -> operationId.equals(receipt(item))).count();
    }

    private String receipt(final ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(receiptKey, PersistentDataType.STRING);
    }

    private void stripReceipt(final Player player, final String operationId) {
        final var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            final ItemStack item = inventory.getItem(slot);
            if (!operationId.equals(receipt(item))) continue;
            final var meta = item.getItemMeta();
            meta.getPersistentDataContainer().remove(receiptKey);
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
        }
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
