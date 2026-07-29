package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.core.Permissions;
import hu.taliann.icesmp.gui.InvseeGUI;
import hu.taliann.icesmp.gui.InvseeHolder;
import hu.taliann.icesmp.moderation.PaperEntityTaskSubmission;
import hu.taliann.icesmp.moderation.InventoryEscrowGate;
import hu.taliann.icesmp.moderation.InventoryEscrowQueue;
import hu.taliann.icesmp.moderation.InventoryTransferBarrier;
import hu.taliann.icesmp.moderation.InventoryWriteRecovery;
import hu.taliann.icesmp.moderation.InvseeEscrowSchema;
import hu.taliann.icesmp.moderation.TaskLease;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Live online inventory/ender-chest inspection. Reads and writes happen on the target entity
 * scheduler; GUI/cursor mutations happen on the admin entity scheduler. A one-way claim gate
 * prevents the inserted cursor stack from being both returned and committed when either player
 * disconnects between the two scheduler hops.
 */
public final class InvseeManager implements PersistentStore, PlayerStateCleanup {

    public record Snapshot(ItemStack[] storage, ItemStack[] armor, ItemStack offHand, ItemStack[] ender) {
        public Snapshot {
            storage = cloneArray(storage);
            armor = cloneArray(armor);
            offHand = cloneItem(offHand);
            ender = cloneArray(ender);
        }
    }

    private record TargetSlot(InvseeHolder.View view, int index, Kind kind) {
        enum Kind { STORAGE, ARMOR, OFFHAND, ENDER }
    }

    private static final class Session {
        private final UUID sessionId = UUID.randomUUID();
        private final UUID viewerId;
        private final String viewerName;
        private final UUID targetId;
        private final String targetName;
        private final InvseeHolder.Mode mode;
        private volatile InvseeHolder.View view;
        private final AtomicBoolean refreshInFlight = new AtomicBoolean();
        private volatile TaskLease<ScheduledTask> refreshLease;
        private volatile PendingTransfer pending;
        private volatile boolean transitioning;

        private Session(final UUID viewerId, final String viewerName, final UUID targetId, final String targetName,
                        final InvseeHolder.Mode mode, final InvseeHolder.View view) {
            this.viewerId = viewerId;
            this.viewerName = viewerName;
            this.targetId = targetId;
            this.targetName = targetName;
            this.mode = mode;
            this.view = view;
        }
    }

    private static final class PendingTransfer {
        private final ItemStack inserted;
        private final InventoryEscrowGate gate = new InventoryEscrowGate();
        private volatile ItemStack displaced;
        private volatile boolean viewerAbsent;
        private final AtomicBoolean lifecycleReleased = new AtomicBoolean();

        private PendingTransfer(final ItemStack inserted) {
            this.inserted = cloneItem(inserted);
        }

        private boolean claimTarget() { return gate.claimTarget(); }
        private boolean claimReturn() { return gate.claimInsertedReturn(); }
        private boolean abortTargetAndClaimReturn() { return gate.abortTargetAndClaimInsertedReturn(); }
        private void prepareCompletion(final ItemStack displaced) {
            this.displaced = cloneItem(displaced);
        }
        private void publishCompletion() { gate.completeTargetWrite(); }
        private InventoryEscrowGate.State state() { return gate.state(); }
        private boolean claimDisplacedReturn() { return gate.claimDisplacedReturn(); }
        private boolean claimLifecycleRelease() { return lifecycleReleased.compareAndSet(false, true); }
    }

    private static final int MAX_ESCROW_PLAYERS = 10_000;
    private static final int MAX_ESCROW_ITEMS = 100_000;

    private final JavaPlugin plugin;
    private final MessageManager messages;
    private final ModerationManager moderationManager;
    private final File escrowFile;
    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final InventoryEscrowQueue<ItemStack> pendingReturns = new InventoryEscrowQueue<>(ItemStack::clone);
    private final InventoryTransferBarrier transferBarrier = new InventoryTransferBarrier();
    private volatile boolean shuttingDown;

    public InvseeManager(final JavaPlugin plugin, final MessageManager messages,
                         final ModerationManager moderationManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.moderationManager = moderationManager;
        this.escrowFile = new File(plugin.getDataFolder(), "invsee-escrow.yml");
        YamlStore.registerCriticalWrite(escrowFile);
    }

    @Override
    public void load() {
        final YamlConfiguration yaml = YamlStore.loadTracked(escrowFile, plugin.getLogger());
        try {
            if (escrowFile.isFile() && yaml.getKeys(false).isEmpty()) {
                throw new IllegalArgumentException("existing authoritative escrow file is empty");
            }
            final Map<UUID, List<ItemStack>> loaded = decodeEscrow(yaml);
            pendingReturns.replace(loaded);
        } catch (final RuntimeException invalid) {
            YamlStore.failCorrupt(escrowFile, plugin.getLogger(),
                    "Az invsee escrow szemantikailag érvénytelen: " + invalid.getMessage());
        }
    }

    @Override
    public void save() {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", InvseeEscrowSchema.VERSION);
        for (final Map.Entry<UUID, List<ItemStack>> entry : pendingReturns.snapshot().entrySet()) {
            yaml.set("returns." + entry.getKey(), cloneList(entry.getValue()));
        }
        try {
            YamlStore.saveAtomic(escrowFile, yaml);
        } catch (final IOException failure) {
            throw new UncheckedIOException("Failed to save invsee-escrow.yml", failure);
        }
    }

    private static Map<UUID, List<ItemStack>> decodeEscrow(final YamlConfiguration yaml) {
        if (yaml.getKeys(false).isEmpty()) {
            return Map.of();
        }
        final Map<UUID, List<ItemStack>> loaded = new LinkedHashMap<>();
        final ConfigurationSection returns = yaml.getConfigurationSection("returns");
        if (yaml.contains("returns") && returns == null) {
            throw new IllegalArgumentException("returns must be a section");
        }
        final Map<String, Object> rawReturns = new LinkedHashMap<>();
        if (returns != null) {
            for (final String key : returns.getKeys(false)) {
                rawReturns.put(key, returns.get(key));
            }
        }
        for (final InvseeEscrowSchema.Entry entry : InvseeEscrowSchema.validate(
                yaml.getKeys(false), yaml.get("schema-version"), rawReturns,
                MAX_ESCROW_PLAYERS, MAX_ESCROW_ITEMS)) {
            final List<ItemStack> items = new ArrayList<>(entry.payloads().size());
            for (final Object value : entry.payloads()) {
                if (!(value instanceof ItemStack item) || isEmpty(item)) {
                    throw new IllegalArgumentException("escrow entry contains an invalid item: " + entry.playerId());
                }
                items.add(item.clone());
            }
            loaded.put(entry.playerId(), List.copyOf(items));
        }
        return loaded;
    }

    public void open(final Player viewer, final Player target, final InvseeHolder.Mode mode,
                     final InvseeHolder.View view) {
        if (shuttingDown) {
            viewer.sendMessage(messages.get("moderation.invsee-shutting-down",
                    "&cAz inventory-nézet éppen leáll."));
            return;
        }
        if (viewer.getUniqueId().equals(target.getUniqueId())) {
            viewer.sendMessage(messages.get("moderation.invsee-self", "&cA saját inventorydat nem nyithatod meg adminnézetben."));
            return;
        }
        closeSession(viewer.getUniqueId());
        final Session session = new Session(viewer.getUniqueId(), viewer.getName(), target.getUniqueId(),
                target.getName(), mode, view);
        sessions.put(viewer.getUniqueId(), session);
        capture(target, snapshot -> PaperEntityTaskSubmission.run(plugin, viewer.getScheduler(), () -> {
            if (!viewer.isOnline() || sessions.get(viewer.getUniqueId()) != session) {
                return;
            }
            final InvseeHolder holder = new InvseeHolder(session.sessionId, session.targetId,
                    session.targetName, session.view, session.mode);
            viewer.openInventory(InvseeGUI.create(holder, snapshot, messages));
            startRefresh(viewer, session);
        }, () -> closeSession(viewer.getUniqueId())), () -> {
            sessions.remove(viewer.getUniqueId(), session);
            PaperEntityTaskSubmission.run(plugin, viewer.getScheduler(),
                    () -> viewer.sendMessage(messages.get(
                            "moderation.invsee-target-left", "&cA céljátékos kilépett.")),
                    () -> { });
        });
    }

    private void startRefresh(final Player viewer, final Session session) {
        final TaskLease<ScheduledTask> previous = session.refreshLease;
        if (previous != null) {
            previous.retire();
        }
        final TaskLease<ScheduledTask> lease = new TaskLease<>(ScheduledTask::cancel);
        session.refreshLease = lease;
        final ScheduledTask scheduled;
        try {
            scheduled = viewer.getScheduler().runAtFixedRate(plugin,
                    task -> requestRefresh(viewer, session), () -> {
                        lease.retire();
                        if (session.refreshLease == lease) {
                            session.refreshLease = null;
                        }
                        closeSession(viewer.getUniqueId());
                    }, 10L, 10L);
        } catch (final RuntimeException schedulingFailure) {
            lease.retire();
            if (session.refreshLease == lease) {
                session.refreshLease = null;
            }
            closeSession(viewer.getUniqueId());
            return;
        }
        if (!lease.publish(scheduled)) {
            if (session.refreshLease == lease) {
                session.refreshLease = null;
            }
            closeSession(viewer.getUniqueId());
        }
    }

    private void requestRefresh(final Player viewer, final Session session) {
        if (shuttingDown || sessions.get(viewer.getUniqueId()) != session
                || !session.refreshInFlight.compareAndSet(false, true)) {
            return;
        }
        final Player target = Bukkit.getPlayer(session.targetId);
        if (target == null) {
            session.refreshInFlight.set(false);
            closeFromViewerThread(viewer, session, "&cA céljátékos kilépett.");
            return;
        }
        capture(target, snapshot -> PaperEntityTaskSubmission.run(plugin, viewer.getScheduler(), () -> {
            session.refreshInFlight.set(false);
            if (!viewer.isOnline() || sessions.get(viewer.getUniqueId()) != session) {
                return;
            }
            if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof InvseeHolder holder
                    && holder.sessionId().equals(session.sessionId) && session.pending == null) {
                InvseeGUI.update(viewer.getOpenInventory().getTopInventory(), holder, snapshot, messages);
            }
        }, () -> session.refreshInFlight.set(false)), () -> {
            session.refreshInFlight.set(false);
            PaperEntityTaskSubmission.run(plugin, viewer.getScheduler(),
                    () -> closeFromViewerThread(viewer, session, "&cA céljátékos kilépett."),
                    () -> closeSession(viewer.getUniqueId()));
        });
    }

    private void capture(final Player target, final java.util.function.Consumer<Snapshot> callback,
                         final Runnable retired) {
        PaperEntityTaskSubmission.run(plugin, target.getScheduler(), () -> {
            if (!target.isOnline()) {
                retired.run();
                return;
            }
            callback.accept(new Snapshot(target.getInventory().getStorageContents(),
                    target.getInventory().getArmorContents(), target.getInventory().getItemInOffHand(),
                    target.getEnderChest().getContents()));
        }, retired);
    }

    public void switchView(final Player viewer, final InvseeHolder holder) {
        final Session session = validSession(viewer, holder);
        if (session == null || session.pending != null) {
            return;
        }
        final Player target = Bukkit.getPlayer(session.targetId);
        if (target == null) {
            closeFromViewerThread(viewer, session, "&cA céljátékos kilépett.");
            return;
        }
        session.transitioning = true;
        session.view = holder.view() == InvseeHolder.View.MAIN ? InvseeHolder.View.ENDER : InvseeHolder.View.MAIN;
        capture(target, snapshot -> PaperEntityTaskSubmission.run(plugin, viewer.getScheduler(), () -> {
            if (!viewer.isOnline() || sessions.get(viewer.getUniqueId()) != session) {
                session.transitioning = false;
                return;
            }
            final InvseeHolder next = new InvseeHolder(session.sessionId, session.targetId, session.targetName,
                    session.view, session.mode);
            viewer.openInventory(InvseeGUI.create(next, snapshot, messages));
            session.transitioning = false;
        }, () -> session.transitioning = false), () -> PaperEntityTaskSubmission.run(plugin,
                viewer.getScheduler(),
                () -> closeFromViewerThread(viewer, session, "&cA céljátékos kilépett."),
                () -> closeSession(viewer.getUniqueId())));
    }

    public void beginSwap(final Player viewer, final InvseeHolder holder, final int rawSlot) {
        final Session session = validSession(viewer, holder);
        if (session == null || session.mode != InvseeHolder.Mode.EDIT
                || !viewer.hasPermission(Permissions.MODERATION_INVENTORY_EDIT)
                || session.pending != null) {
            return;
        }
        final TargetSlot targetSlot = mapSlot(holder.view(), rawSlot);
        if (targetSlot == null || shuttingDown || !transferBarrier.reserve()) {
            return;
        }
        final ItemStack inserted = cloneItem(viewer.getItemOnCursor());
        final PendingTransfer transfer = new PendingTransfer(inserted);
        session.pending = transfer;
        // The admin owns this mutation and the event runs on their scheduler. From this point the
        // transfer object is the only owner of the removed cursor stack until one claim wins.
        viewer.setItemOnCursor(null);

        final Player target = Bukkit.getPlayer(session.targetId);
        if (target == null) {
            failBeforeTarget(viewer, session, transfer);
            return;
        }
        PaperEntityTaskSubmission.run(plugin, target.getScheduler(), () -> {
            if (!target.isOnline() || !transfer.claimTarget()) {
                if (transfer.state() == InventoryEscrowGate.State.INIT) {
                    failBeforeTarget(viewer, session, transfer);
                }
                return;
            }
            final ItemStack displaced = readTargetSlot(target, targetSlot);
            try {
                writeTargetSlot(target, targetSlot, transfer.inserted);
            } catch (final RuntimeException writeFailure) {
                recoverTargetWriteFailure(viewer, session, holder, rawSlot, target, targetSlot,
                        transfer, displaced, writeFailure);
                return;
            }
            completeTargetTransfer(viewer, session, holder, rawSlot, target, transfer, displaced);
        }, () -> failBeforeTarget(viewer, session, transfer));
    }

    private void recoverTargetWriteFailure(final Player viewer, final Session session,
                                           final InvseeHolder holder, final int rawSlot,
                                           final Player target, final TargetSlot targetSlot,
                                           final PendingTransfer transfer, final ItemStack displaced,
                                           final RuntimeException writeFailure) {
        boolean rollbackCompleted = false;
        try {
            writeTargetSlot(target, targetSlot, displaced);
            rollbackCompleted = true;
        } catch (final RuntimeException rollbackFailure) {
            writeFailure.addSuppressed(rollbackFailure);
            plugin.getLogger().severe("Invsee cél-slot rollback hibát jelzett: " + rollbackFailure);
        }

        InventoryWriteRecovery.Outcome outcome = rollbackCompleted
                ? InventoryWriteRecovery.Outcome.ROLLED_BACK : InventoryWriteRecovery.Outcome.UNKNOWN;
        if (!rollbackCompleted) {
            try {
                final ItemStack current = readTargetSlot(target, targetSlot);
                outcome = InventoryWriteRecovery.classify(
                        sameItem(current, transfer.inserted), sameItem(current, displaced));
            } catch (final RuntimeException readFailure) {
                writeFailure.addSuppressed(readFailure);
            }
        }

        if (outcome == InventoryWriteRecovery.Outcome.COMMITTED) {
            plugin.getLogger().warning("Invsee cél-slot írás hibát jelzett, de a post-state a commitot igazolja: "
                    + writeFailure);
            completeTargetTransfer(viewer, session, holder, rawSlot, target, transfer, displaced);
            return;
        }
        if (outcome == InventoryWriteRecovery.Outcome.ROLLED_BACK) {
            if (transfer.abortTargetAndClaimReturn()) {
                queueReturn(session.viewerId, transfer.inserted);
                notifyFailedTransfer(viewer, session, transfer);
            } else {
                finishTransfer(transfer);
            }
            plugin.getLogger().warning("Invsee cél-slot írás sikertelen, a pre-state helyreállt: " + writeFailure);
            return;
        }

        // Neither the pre-state nor the intended post-state is observable. Returning the inserted
        // item here could duplicate it if the target owns it, so stop admission and disable instead
        // of inventing a second owner. The full exception chain identifies the slot for recovery.
        session.pending = null;
        shuttingDown = true;
        transferBarrier.close();
        finishTransfer(transfer);
        plugin.getLogger().severe("Invsee cél-slot állapota nem egyeztethető össze; automatikus item-visszaadás "
                + "megtagadva (target=" + session.targetId + ", view=" + holder.view()
                + ", slot=" + rawSlot + "): " + writeFailure);
        try {
            Bukkit.getGlobalRegionScheduler().run(plugin,
                    task -> Bukkit.getPluginManager().disablePlugin(plugin));
        } catch (final RuntimeException schedulingFailure) {
            plugin.getLogger().severe("Invsee fail-closed plugin-disable ütemezése sikertelen: "
                    + schedulingFailure);
        }
    }

    private void completeTargetTransfer(final Player viewer, final Session session,
                                        final InvseeHolder holder, final int rawSlot,
                                        final Player target, final PendingTransfer transfer,
                                        final ItemStack displaced) {
        // Keep the gate TARGET_CLAIMED while publishing the displaced stack. Shutdown therefore
        // waits for this callback instead of observing COMPLETE before the durable queue exists.
        transfer.prepareCompletion(displaced);
        queueReturn(session.viewerId, transfer.displaced);
        transfer.publishCompletion();
        try {
            moderationManager.logInventoryEditAsync(session.viewerId, session.viewerName,
                    target.getUniqueId(), target.getName(), holder.view().name(), rawSlot,
                    transfer.inserted, displaced);
        } catch (final RuntimeException auditFailure) {
            plugin.getLogger().warning("Invsee audit ütemezése sikertelen: " + auditFailure);
        }
        PaperEntityTaskSubmission.run(plugin, viewer.getScheduler(),
                () -> finishAfterTarget(viewer, session, transfer),
                () -> finishWithoutViewer(session, transfer));
    }

    private void finishAfterTarget(final Player viewer, final Session session, final PendingTransfer transfer) {
        try {
            if (shuttingDown || !viewer.isOnline()
                    || sessions.get(viewer.getUniqueId()) != session || transfer.viewerAbsent) {
                return;
            }
            session.pending = null;
            final ItemStack displaced = claimQueuedReturn(session.viewerId, transfer.displaced);
            if (!isEmpty(displaced)) {
                returnClaimedItem(viewer, session.viewerId, displaced, true);
            }
            requestRefresh(viewer, session);
        } finally {
            finishTransfer(transfer);
        }
    }

    private void failBeforeTarget(final Player viewer, final Session session, final PendingTransfer transfer) {
        if (!transfer.claimReturn()) {
            return;
        }
        queueReturn(session.viewerId, transfer.inserted);
        notifyFailedTransfer(viewer, session, transfer);
    }

    private void notifyFailedTransfer(final Player viewer, final Session session,
                                      final PendingTransfer transfer) {
        PaperEntityTaskSubmission.run(plugin, viewer.getScheduler(), () -> {
            try {
                if (!shuttingDown && viewer.isOnline()) {
                    final ItemStack inserted = claimQueuedReturn(session.viewerId, transfer.inserted);
                    if (!isEmpty(inserted)) {
                        returnClaimedItem(viewer, session.viewerId, inserted, true);
                    }
                    if (sessions.get(viewer.getUniqueId()) == session) {
                        session.pending = null;
                    }
                    viewer.sendMessage(messages.get("moderation.invsee-edit-failed",
                            "&cA céljátékos kilépett; a kurzortárgyad visszakaptad."));
                }
            } finally {
                finishTransfer(transfer);
            }
        }, () -> finishWithoutViewer(session, transfer));
    }

    private void finishWithoutViewer(final Session session, final PendingTransfer transfer) {
        transfer.viewerAbsent = true;
        if (sessions.get(session.viewerId) == session) {
            session.pending = null;
        }
        finishTransfer(transfer);
    }

    public boolean hasPending(final Player viewer, final InvseeHolder holder) {
        final Session session = validSession(viewer, holder);
        return session != null && session.pending != null;
    }

    public void handleClose(final Player viewer, final InvseeHolder holder) {
        final Session session = validSession(viewer, holder);
        if (session == null || session.transitioning) {
            return;
        }
        closeSession(viewer.getUniqueId());
    }

    public void closeFromGui(final Player viewer, final InvseeHolder holder) {
        final Session session = validSession(viewer, holder);
        if (session != null) {
            closeSession(viewer.getUniqueId());
            viewer.closeInventory();
        }
    }

    private Session validSession(final Player viewer, final InvseeHolder holder) {
        final Session session = sessions.get(viewer.getUniqueId());
        return session != null && session.sessionId.equals(holder.sessionId()) ? session : null;
    }

    private void closeFromViewerThread(final Player viewer, final Session session, final String message) {
        if (sessions.remove(viewer.getUniqueId(), session)) {
            cleanupSession(session, viewer);
            viewer.closeInventory();
            viewer.sendMessage(messages.get("moderation.invsee-closed", message));
        }
    }

    private void closeSession(final UUID viewerId) {
        final Session session = sessions.remove(viewerId);
        if (session == null) {
            return;
        }
        final Player viewer = Bukkit.getPlayer(viewerId);
        if (viewer == null || !viewer.isOnline()) {
            cleanupSession(session, null);
            return;
        }
        // This helper is also called from target/retired callbacks. Never mutate the admin's
        // cursor or inventory from that foreign scheduler.
        PaperEntityTaskSubmission.run(plugin, viewer.getScheduler(),
                () -> cleanupSession(session, viewer), () -> cleanupSession(session, null));
    }

    private void cleanupSession(final Session session, final Player viewer) {
        final TaskLease<ScheduledTask> refresh = session.refreshLease;
        session.refreshLease = null;
        if (refresh != null) {
            refresh.retire();
        }
        final PendingTransfer transfer = session.pending;
        session.pending = null;
        if (transfer == null) {
            return;
        }
        transfer.viewerAbsent = true;
        if (transfer.claimReturn()) {
            queueReturn(session.viewerId, transfer.inserted);
            if (!shuttingDown && viewer != null && viewer.isOnline()) {
                final ItemStack inserted = claimQueuedReturn(session.viewerId, transfer.inserted);
                if (!isEmpty(inserted)) {
                    returnClaimedItem(viewer, session.viewerId, inserted, false);
                }
            }
            finishTransfer(transfer);
        } else if (transfer.state() == InventoryEscrowGate.State.COMPLETE) {
            // Target completion has already placed the displaced item in pendingReturns.
            if (!shuttingDown && viewer != null && viewer.isOnline()) {
                final ItemStack displaced = claimQueuedReturn(session.viewerId, transfer.displaced);
                if (!isEmpty(displaced)) {
                    returnClaimedItem(viewer, session.viewerId, displaced, false);
                }
            }
            finishTransfer(transfer);
        }
    }

    private void queueReturn(final UUID viewerId, final ItemStack item) {
        if (isEmpty(item)) {
            return;
        }
        pendingReturns.add(viewerId, item);
    }

    /** Claims one equivalent queued stack; equal duplicate stacks remain count-safe and interchangeable. */
    private ItemStack claimQueuedReturn(final UUID viewerId, final ItemStack expected) {
        if (isEmpty(expected)) {
            return null;
        }
        return pendingReturns.claimMatching(viewerId, candidate -> candidate.equals(expected));
    }

    private void finishTransfer(final PendingTransfer transfer) {
        if (transfer.claimLifecycleRelease()) {
            transferBarrier.release();
        }
    }

    /** Restores edit escrow on reconnect, on the player's own scheduler. */
    public void restorePending(final Player player) {
        int restored = 0;
        ItemStack item;
        while ((item = pendingReturns.claimFirst(player.getUniqueId())) != null) {
            try {
                returnItem(player, item, false);
                restored++;
            } catch (final RuntimeException failure) {
                pendingReturns.restoreFirst(player.getUniqueId(), item);
                plugin.getLogger().warning("Invsee escrow visszaadása megszakadt; a rekord megmaradt: "
                        + failure.getMessage());
                break;
            }
        }
        if (restored > 0) {
            player.sendMessage(messages.get("moderation.invsee-escrow-restored",
                    "&aA megszakadt inventory-szerkesztés tárgyai visszakerültek hozzád."));
        }
    }

    private void returnClaimedItem(final Player player, final UUID playerId,
                                   final ItemStack item, final boolean preferCursor) {
        try {
            returnItem(player, item, preferCursor);
        } catch (final RuntimeException failure) {
            pendingReturns.restoreFirst(playerId, item);
            throw failure;
        }
    }

    private static void returnItem(final Player player, final ItemStack item, final boolean preferCursor) {
        if (isEmpty(item)) {
            return;
        }
        if (preferCursor && isEmpty(player.getItemOnCursor())) {
            player.setItemOnCursor(item.clone());
            return;
        }
        final Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
        for (final ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private static TargetSlot mapSlot(final InvseeHolder.View view, final int rawSlot) {
        if (view == InvseeHolder.View.ENDER) {
            return rawSlot >= 0 && rawSlot < 27
                    ? new TargetSlot(view, rawSlot, TargetSlot.Kind.ENDER) : null;
        }
        if (rawSlot >= 0 && rawSlot < 36) {
            return new TargetSlot(view, rawSlot, TargetSlot.Kind.STORAGE);
        }
        if (rawSlot >= 36 && rawSlot < 40) {
            return new TargetSlot(view, rawSlot - 36, TargetSlot.Kind.ARMOR);
        }
        return rawSlot == 40 ? new TargetSlot(view, 0, TargetSlot.Kind.OFFHAND) : null;
    }

    private static ItemStack readTargetSlot(final Player target, final TargetSlot slot) {
        return switch (slot.kind) {
            case STORAGE -> cloneItem(target.getInventory().getItem(slot.index));
            case ARMOR -> cloneItem(target.getInventory().getArmorContents()[slot.index]);
            case OFFHAND -> cloneItem(target.getInventory().getItemInOffHand());
            case ENDER -> cloneItem(target.getEnderChest().getItem(slot.index));
        };
    }

    private static void writeTargetSlot(final Player target, final TargetSlot slot, final ItemStack item) {
        final ItemStack value = isEmpty(item) ? null : item.clone();
        switch (slot.kind) {
            case STORAGE -> target.getInventory().setItem(slot.index, value);
            case ARMOR -> {
                final ItemStack[] armor = target.getInventory().getArmorContents();
                armor[slot.index] = value;
                target.getInventory().setArmorContents(armor);
            }
            case OFFHAND -> target.getInventory().setItemInOffHand(value);
            case ENDER -> target.getEnderChest().setItem(slot.index, value);
        }
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        final Session own = sessions.remove(playerId);
        if (own != null) {
            final Player viewer = Bukkit.getPlayer(playerId);
            // Player quit/kick events already run on the player's scheduler. Returning directly
            // here is safe; when the player is no longer available, keep it for reconnect.
            cleanupSession(own, viewer != null && viewer.isOnline() ? viewer : null);
        }
        for (final Session session : List.copyOf(sessions.values())) {
            if (session.targetId.equals(playerId) && sessions.remove(session.viewerId, session)) {
                final Player viewer = Bukkit.getPlayer(session.viewerId);
                if (viewer != null) {
                    PaperEntityTaskSubmission.run(plugin, viewer.getScheduler(), () -> {
                        cleanupSession(session, viewer);
                        if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof InvseeHolder holder
                                && holder.sessionId().equals(session.sessionId)) {
                            viewer.closeInventory();
                        }
                        viewer.sendMessage(messages.get("moderation.invsee-target-left",
                                "&cA céljátékos kilépett; a live nézet bezárult."));
                    }, () -> cleanupSession(session, null));
                } else {
                    cleanupSession(session, null);
                }
            }
        }
    }

    /** Closes transient live views on config reload; escrow is returned exactly once. */
    public void reload() {
        closeAll(false);
    }

    /**
     * Stops new edits, converts every non-target-owned transfer into durable escrow and waits for
     * already claimed target writes/callbacks. Must run before the common final-save gate closes.
     */
    public boolean prepareShutdown(final long timeoutMillis) {
        shuttingDown = true;
        transferBarrier.close();
        terminalizeSessions();
        final boolean drained = transferBarrier.awaitDrained(timeoutMillis);
        // An edit may have reserved immediately before close and published its Session.pending after
        // the first pass. A second pass catches that publication after the barrier has drained.
        terminalizeSessions();
        return drained;
    }

    public void shutdown() {
        shuttingDown = true;
        terminalizeSessions();
    }

    private void terminalizeSessions() {
        for (final Session session : List.copyOf(sessions.values())) {
            if (!sessions.remove(session.viewerId, session)) {
                continue;
            }
            final TaskLease<ScheduledTask> refresh = session.refreshLease;
            session.refreshLease = null;
            if (refresh != null) {
                refresh.retire();
            }
            final PendingTransfer transfer = session.pending;
            session.pending = null;
            if (transfer != null) {
                transfer.viewerAbsent = true;
                if (transfer.claimReturn()) {
                    queueReturn(session.viewerId, transfer.inserted);
                    finishTransfer(transfer);
                } else if (transfer.state() == InventoryEscrowGate.State.COMPLETE) {
                    // Target completion queued the displaced item before exposing COMPLETE.
                    finishTransfer(transfer);
                }
                // TARGET_CLAIMED and INSERTED_RETURN_CLAIMED are owned by already-admitted
                // callbacks; prepareShutdown waits for those owners through transferBarrier.
            }
            final Player viewer = Bukkit.getPlayer(session.viewerId);
            if (viewer != null) {
                PaperEntityTaskSubmission.run(plugin, viewer.getScheduler(), () -> {
                    if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof InvseeHolder holder
                            && holder.sessionId().equals(session.sessionId)) {
                        viewer.closeInventory();
                    }
                }, () -> { });
            }
        }
    }

    private void closeAll(final boolean terminal) {
        if (terminal) {
            shuttingDown = true;
        }
        for (final Session session : List.copyOf(sessions.values())) {
            if (sessions.remove(session.viewerId, session)) {
                final Player viewer = Bukkit.getPlayer(session.viewerId);
                if (viewer != null && viewer.isOnline()) {
                    PaperEntityTaskSubmission.run(plugin, viewer.getScheduler(), () -> {
                        cleanupSession(session, viewer);
                        if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof InvseeHolder) {
                            viewer.closeInventory();
                        }
                    }, () -> cleanupSession(session, null));
                } else {
                    cleanupSession(session, null);
                }
            }
        }
    }

    private static List<ItemStack> cloneList(final List<ItemStack> source) {
        final List<ItemStack> copy = new ArrayList<>(source.size());
        for (final ItemStack item : source) {
            copy.add(item.clone());
        }
        return List.copyOf(copy);
    }

    private static ItemStack[] cloneArray(final ItemStack[] source) {
        if (source == null) {
            return new ItemStack[0];
        }
        final ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = cloneItem(source[i]);
        }
        return copy;
    }

    private static ItemStack cloneItem(final ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }

    private static boolean sameItem(final ItemStack first, final ItemStack second) {
        if (isEmpty(first) || isEmpty(second)) {
            return isEmpty(first) && isEmpty(second);
        }
        return first.equals(second);
    }

    private static boolean isEmpty(final ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }
}
