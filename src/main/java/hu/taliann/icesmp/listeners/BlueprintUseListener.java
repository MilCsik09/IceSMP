package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.items.BlueprintItemFactory;
import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileOperationStore;
import hu.taliann.icesmp.playerprofile.domain.PlayerProfileOperation;
import hu.taliann.icesmp.playerprofile.transaction.PlayerProfileTransactionManager;
import hu.taliann.icesmp.professions.BlueprintRecoveryPolicy;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Restart-durable blueprint learning transaction.
 *
 * <p>The canonical PlayerProfile operation store is the durable receipt authority. A temporary
 * PDC reservation is only an inventory-side transaction witness, never player-state authority:
 * PREPARED receipt -> durable reservation -> durable profession unlock -> durable consumption ->
 * COMMITTED receipt. On restart the PREPARED receipt, reservation witness and canonical learned
 * state deterministically select forward completion or rollback.</p>
 */
public final class BlueprintUseListener implements Listener {

    static final String OPERATION_TYPE = "profession-blueprint-learn-v1";

    private final JavaPlugin plugin;
    private final BlueprintItemFactory blueprintFactory;
    private final ProfessionRecipeCatalog catalog;
    private final ProfessionManager professionManager;
    private final MessageManager messageManager;
    private final PlayerProfileOperationStore operationStore = new PlayerProfileOperationStore();
    private final NamespacedKey reservationKey;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    /**
     * Compatibility for the existing core assembly signature. It resolves the providing IceSMP
     * plugin and delegates to the sole durable constructor; there is no legacy blueprint path.
     */
    public BlueprintUseListener(final BlueprintItemFactory blueprintFactory,
                                final ProfessionRecipeCatalog catalog,
                                final ProfessionManager professionManager,
                                final MessageManager messageManager) {
        this(JavaPlugin.getProvidingPlugin(BlueprintUseListener.class), blueprintFactory,
                catalog, professionManager, messageManager);
    }

    public BlueprintUseListener(final JavaPlugin plugin,
                                final BlueprintItemFactory blueprintFactory,
                                final ProfessionRecipeCatalog catalog,
                                final ProfessionManager professionManager,
                                final MessageManager messageManager) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.blueprintFactory = java.util.Objects.requireNonNull(blueprintFactory, "blueprintFactory");
        this.catalog = java.util.Objects.requireNonNull(catalog, "catalog");
        this.professionManager = java.util.Objects.requireNonNull(professionManager, "professionManager");
        this.messageManager = java.util.Objects.requireNonNull(messageManager, "messageManager");
        this.reservationKey = new NamespacedKey(plugin, "blueprint_reservation_operation");
    }

    @EventHandler
    public void onInteract(final PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        final Player player = event.getPlayer();
        final ItemStack item = player.getInventory().getItemInMainHand();
        final String recipeId = blueprintFactory.recipeIdOf(item);
        if (recipeId == null) return;

        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        final ProfessionRecipeCatalog.Recipe recipe = catalog.get(recipeId);
        if (recipe == null) {
            player.sendMessage(messageManager.get("blueprint-unknown",
                    "&cEz a tervrajz nem tartozik ismert recepthez."));
            return;
        }
        if (professionManager.hasLearnedRecipe(player, recipeId)) {
            player.sendMessage(messageManager.get("blueprint-already-known",
                    "&7Ezt a receptet már ismered."));
            return;
        }
        if (!operationStore.prepared(player.getUniqueId(), OPERATION_TYPE).isEmpty()) {
            player.sendMessage(messageManager.get("blueprint-operation-pending",
                    "&eEgy korábbi tervrajz-művelet helyreállítása még folyamatban van."));
            recoverPrepared(player, 0);
            return;
        }
        if (!inFlight.add(player.getUniqueId())) {
            player.sendMessage(messageManager.get("blueprint-operation-pending",
                    "&eEgy tervrajz-művelet már folyamatban van."));
            return;
        }

        final String operationId = UUID.randomUUID().toString();
        final String fingerprint = fingerprint(recipeId);
        operationStore.prepare(player.getUniqueId(), operationId, OPERATION_TYPE, fingerprint,
                        Map.of("recipe", recipeId))
                .whenComplete((prepared, failure) -> onOwner(player,
                        () -> afterPrepare(player, recipe, operationId, fingerprint, failure)));
    }

    private void afterPrepare(final Player player,
                              final ProfessionRecipeCatalog.Recipe recipe,
                              final String operationId,
                              final String fingerprint,
                              final Throwable failure) {
        if (failure != null) {
            inFlight.remove(player.getUniqueId());
            if (unwrap(failure) instanceof PlayerProfileTransactionManager.LedgerSaturated) {
                player.sendMessage(messageManager.get("blueprint-ledger-saturated",
                        "&cA PlayerProfile műveleti napló megtelt; a tervrajz nem változott. Próbáld újra később."));
            } else {
                player.sendMessage(messageManager.get("blueprint-storage-failed",
                        "&cA recept PlayerProfile tranzakciója nem készíthető elő; a tervrajz nem változott."));
            }
            return;
        }
        if (!player.isOnline()) {
            inFlight.remove(player.getUniqueId());
            return;
        }
        if (professionManager.hasLearnedRecipe(player, recipe.id())) {
            transitionReceipt(player, operationId, fingerprint, false,
                    () -> player.sendMessage(messageManager.get("blueprint-already-known",
                            "&7Ezt a receptet már ismered.")));
            return;
        }

        final ItemStack current = player.getInventory().getItemInMainHand();
        if (!recipe.id().equals(blueprintFactory.recipeIdOf(current))
                || reservationOf(current) != null) {
            transitionReceipt(player, operationId, fingerprint, false,
                    () -> player.sendMessage(messageManager.get("blueprint-item-changed",
                            "&cA tervrajz megváltozott a tranzakció előkészítése közben; semmi sem fogyott el.")));
            return;
        }
        try {
            reserveMainHand(player, current, operationId);
        } catch (final RuntimeException persistenceFailure) {
            inFlight.remove(player.getUniqueId());
            plugin.getLogger().severe("Blueprint reservation persistence failed: op=" + operationId
                    + " player=" + player.getUniqueId() + " error=" + persistenceFailure.getMessage());
            player.sendMessage(messageManager.get("blueprint-recovery-pending",
                    "&cA tervrajz foglalásának mentése bizonytalan; a tartós recovery receipt megmaradt."));
            return;
        }

        professionManager.learnRecipe(player, recipe.id())
                .whenComplete((learned, learnFailure) -> onOwner(player, () ->
                        afterLearn(player, recipe, operationId, fingerprint, learned, learnFailure)));
    }

    private void afterLearn(final Player player,
                            final ProfessionRecipeCatalog.Recipe recipe,
                            final String operationId,
                            final String fingerprint,
                            final Boolean learned,
                            final Throwable failure) {
        if (failure != null || !Boolean.TRUE.equals(learned)) {
            try {
                releaseReservation(player, operationId, recipe.id());
            } catch (final RuntimeException persistenceFailure) {
                inFlight.remove(player.getUniqueId());
                plugin.getLogger().severe("Blueprint reservation rollback failed: op=" + operationId
                        + " player=" + player.getUniqueId() + " error=" + persistenceFailure.getMessage());
                player.sendMessage(messageManager.get("blueprint-recovery-pending",
                        "&cA tervrajz rollbackja nem tartósítható; a PREPARED recovery receipt megmaradt."));
                return;
            }
            transitionReceipt(player, operationId, fingerprint, false, () ->
                    player.sendMessage(messageManager.get(
                            failure == null ? "blueprint-already-known" : "blueprint-storage-failed",
                            failure == null ? "&7Ezt a receptet már ismered."
                                    : "&cA recept mentése meghiúsult; a tervrajz nem fogyott el.")));
            return;
        }

        try {
            consumeReservation(player, operationId, recipe.id());
        } catch (final RuntimeException persistenceFailure) {
            inFlight.remove(player.getUniqueId());
            plugin.getLogger().severe("Blueprint durable consumption failed after Profile unlock: op="
                    + operationId + " player=" + player.getUniqueId() + " error="
                    + persistenceFailure.getMessage());
            player.sendMessage(messageManager.get("blueprint-recovery-pending",
                    "&eA recept már tartósan feloldódott; a tervrajz fogyasztását recovery fejezi be."));
            return;
        }
        transitionReceipt(player, operationId, fingerprint, true, () -> {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.0F);
            player.sendMessage(messageManager.get("blueprint-learned",
                    "&aÚj receptet tanultál: &e%s&a! (Recept-könyv: /profession recipes)",
                    recipe.displayName()));
        });
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        player.getScheduler().runDelayed(plugin, task -> recoverPrepared(player, 0), null, 1L);
    }

    private void recoverPrepared(final Player player, final int attempt) {
        if (!player.isOnline()) return;
        final List<PlayerProfileOperation> pending;
        try {
            pending = operationStore.prepared(player.getUniqueId(), OPERATION_TYPE);
        } catch (final RuntimeException profileNotReady) {
            if (attempt < 5) {
                player.getScheduler().runDelayed(plugin,
                        task -> recoverPrepared(player, attempt + 1), null, 1L);
            } else {
                plugin.getLogger().severe("Blueprint recovery cannot read PlayerProfile receipts for "
                        + player.getUniqueId() + ": " + profileNotReady.getMessage());
            }
            return;
        }
        if (pending.isEmpty()) {
            inFlight.remove(player.getUniqueId());
            return;
        }
        if (!inFlight.add(player.getUniqueId()) && attempt == 0) return;
        recoverAt(player, pending, 0);
    }

    private void recoverAt(final Player player,
                           final List<PlayerProfileOperation> pending,
                           final int index) {
        if (!player.isOnline()) {
            inFlight.remove(player.getUniqueId());
            return;
        }
        if (index >= pending.size()) {
            inFlight.remove(player.getUniqueId());
            return;
        }
        final PlayerProfileOperation operation = pending.get(index);
        final String recipeId = operation.metadata().get("recipe");
        if (recipeId == null || catalog.get(recipeId) == null) {
            inFlight.remove(player.getUniqueId());
            plugin.getLogger().severe("Blueprint recovery requires admin review: invalid recipe metadata op="
                    + operation.operationId() + " player=" + player.getUniqueId());
            player.sendMessage(messageManager.get("blueprint-recovery-review",
                    "&cEgy korábbi tervrajz-művelethez admin recovery szükséges."));
            return;
        }
        final Reserved reserved;
        try {
            reserved = findReservation(player, operation.operationId(), recipeId);
        } catch (final IllegalStateException mismatch) {
            inFlight.remove(player.getUniqueId());
            plugin.getLogger().severe("Blueprint recovery witness mismatch: op=" + operation.operationId()
                    + " player=" + player.getUniqueId() + " error=" + mismatch.getMessage());
            player.sendMessage(messageManager.get("blueprint-recovery-review",
                    "&cEgy korábbi tervrajz-művelethez admin recovery szükséges."));
            return;
        }
        final BlueprintRecoveryPolicy.Decision decision = BlueprintRecoveryPolicy.decide(
                professionManager.hasLearnedRecipe(player, recipeId), reserved != null);
        try {
            switch (decision) {
                case RELEASE_AND_ROLLBACK -> releaseReservation(player,
                        operation.operationId(), recipeId);
                case CONSUME_AND_COMMIT -> consumeReservation(player,
                        operation.operationId(), recipeId);
                case ROLLBACK_UNTOUCHED, COMMIT_CONSUMED -> { }
            }
        } catch (final RuntimeException persistenceFailure) {
            inFlight.remove(player.getUniqueId());
            plugin.getLogger().severe("Blueprint recovery inventory persistence failed: op="
                    + operation.operationId() + " player=" + player.getUniqueId()
                    + " decision=" + decision + " error=" + persistenceFailure.getMessage());
            return;
        }
        final boolean commit = decision == BlueprintRecoveryPolicy.Decision.CONSUME_AND_COMMIT
                || decision == BlueprintRecoveryPolicy.Decision.COMMIT_CONSUMED;
        final var transition = commit
                ? operationStore.commit(player.getUniqueId(), operation.operationId(),
                        OPERATION_TYPE, operation.fingerprint())
                : operationStore.rollback(player.getUniqueId(), operation.operationId(),
                        OPERATION_TYPE, operation.fingerprint());
        transition.whenComplete((ignored, failure) -> onOwner(player, () -> {
            if (failure != null) {
                inFlight.remove(player.getUniqueId());
                plugin.getLogger().severe("Blueprint recovery receipt transition failed: op="
                        + operation.operationId() + " player=" + player.getUniqueId()
                        + " error=" + unwrap(failure).getMessage());
                return;
            }
            recoverAt(player, pending, index + 1);
        }));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(final PlayerDropItemEvent event) {
        if (reservationOf(event.getItemDrop().getItemStack()) != null) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(final EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && hasReservation(player)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && hasReservation(player)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && hasReservation(player)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHand(final PlayerSwapHandItemsEvent event) {
        if (hasReservation(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeldSlot(final PlayerItemHeldEvent event) {
        if (hasReservation(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(final PlayerDeathEvent event) {
        final Iterator<ItemStack> drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            final ItemStack item = drops.next();
            if (reservationOf(item) == null) continue;
            drops.remove();
            event.getItemsToKeep().add(item);
        }
    }

    private void transitionReceipt(final Player player,
                                   final String operationId,
                                   final String fingerprint,
                                   final boolean commit,
                                   final Runnable success) {
        final var transition = commit
                ? operationStore.commit(player.getUniqueId(), operationId, OPERATION_TYPE, fingerprint)
                : operationStore.rollback(player.getUniqueId(), operationId, OPERATION_TYPE, fingerprint);
        transition.whenComplete((ignored, failure) -> onOwner(player, () -> {
            inFlight.remove(player.getUniqueId());
            if (failure != null) {
                plugin.getLogger().severe("Blueprint receipt transition failed: op=" + operationId
                        + " player=" + player.getUniqueId() + " error=" + unwrap(failure).getMessage());
                player.sendMessage(messageManager.get("blueprint-recovery-pending",
                        "&eA művelet eredménye tartós, de a recovery receipt lezárása későbbi retryra vár."));
                return;
            }
            success.run();
        }));
    }

    private void reserveMainHand(final Player player,
                                 final ItemStack current,
                                 final String operationId) {
        final int slot = player.getInventory().getHeldItemSlot();
        final ItemStack reserved = current.clone();
        setReservation(reserved, operationId);
        persistSlotChange(player, slot, current.clone(), reserved);
    }

    private void releaseReservation(final Player player,
                                    final String operationId,
                                    final String recipeId) {
        final Reserved reserved = requireReservation(player, operationId, recipeId);
        final ItemStack released = reserved.item().clone();
        setReservation(released, null);
        persistSlotChange(player, reserved.slot(), reserved.item().clone(), released);
    }

    private void consumeReservation(final Player player,
                                    final String operationId,
                                    final String recipeId) {
        final Reserved reserved = requireReservation(player, operationId, recipeId);
        final ItemStack remaining;
        if (reserved.item().getAmount() <= 1) {
            remaining = null;
        } else {
            remaining = reserved.item().clone();
            remaining.setAmount(remaining.getAmount() - 1);
            setReservation(remaining, null);
        }
        persistSlotChange(player, reserved.slot(), reserved.item().clone(), remaining);
    }

    private Reserved requireReservation(final Player player,
                                        final String operationId,
                                        final String recipeId) {
        final Reserved reserved = findReservation(player, operationId, recipeId);
        if (reserved == null) throw new IllegalStateException("reservation witness missing");
        return reserved;
    }

    private Reserved findReservation(final Player player,
                                     final String operationId,
                                     final String recipeId) {
        final int storageSize = player.getInventory().getStorageContents().length;
        for (int slot = 0; slot < storageSize; slot++) {
            final ItemStack item = player.getInventory().getItem(slot);
            if (!operationId.equals(reservationOf(item))) continue;
            final String witnessedRecipe = blueprintFactory.recipeIdOf(item);
            if (!recipeId.equals(witnessedRecipe)) {
                throw new IllegalStateException("reservation recipe mismatch");
            }
            return new Reserved(slot, item.clone());
        }
        return null;
    }

    private boolean hasReservation(final Player player) {
        final int storageSize = player.getInventory().getStorageContents().length;
        for (int slot = 0; slot < storageSize; slot++) {
            if (reservationOf(player.getInventory().getItem(slot)) != null) return true;
        }
        return false;
    }

    private String reservationOf(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(
                reservationKey, PersistentDataType.STRING);
    }

    private void setReservation(final ItemStack item, final String operationId) {
        if (item == null) return;
        final var meta = item.getItemMeta();
        if (operationId == null) {
            meta.getPersistentDataContainer().remove(reservationKey);
        } else {
            meta.getPersistentDataContainer().set(reservationKey,
                    PersistentDataType.STRING, operationId);
        }
        item.setItemMeta(meta);
    }

    private static void persistSlotChange(final Player player,
                                          final int slot,
                                          final ItemStack before,
                                          final ItemStack after) {
        player.getInventory().setItem(slot, after == null ? null : after.clone());
        try {
            player.saveData();
        } catch (final RuntimeException persistenceFailure) {
            player.getInventory().setItem(slot, before == null ? null : before.clone());
            try {
                player.saveData();
            } catch (final RuntimeException rollbackFailure) {
                persistenceFailure.addSuppressed(rollbackFailure);
            }
            throw persistenceFailure;
        }
    }

    private void onOwner(final Player player, final Runnable action) {
        if (!player.isOnline()) {
            inFlight.remove(player.getUniqueId());
            return;
        }
        try {
            player.getScheduler().run(plugin, task -> action.run(),
                    () -> inFlight.remove(player.getUniqueId()));
        } catch (final RuntimeException schedulerRejected) {
            inFlight.remove(player.getUniqueId());
        }
    }

    private static String fingerprint(final String recipeId) {
        final String value = "blueprint-" + recipeId;
        if (value.length() > 128) throw new IllegalArgumentException("blueprint recipe id too long");
        return value;
    }

    private static Throwable unwrap(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private record Reserved(int slot, ItemStack item) { }
}
