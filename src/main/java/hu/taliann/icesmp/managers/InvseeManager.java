package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.core.Permissions;
import hu.taliann.icesmp.gui.InvseeGUI;
import hu.taliann.icesmp.gui.InvseeHolder;
import hu.taliann.icesmp.moderation.InventoryEscrowGate;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Live online inventory/ender-chest inspection. Reads and writes happen on the target entity
 * scheduler; GUI/cursor mutations happen on the admin entity scheduler. A one-way claim gate
 * prevents the inserted cursor stack from being both returned and committed when either player
 * disconnects between the two scheduler hops.
 */
public final class InvseeManager implements PlayerStateCleanup {

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
        private volatile ScheduledTask refreshTask;
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
        private final UUID id = UUID.randomUUID();
        private final ItemStack inserted;
        private final InventoryEscrowGate gate = new InventoryEscrowGate();
        private volatile ItemStack displaced;
        private volatile boolean viewerAbsent;

        private PendingTransfer(final ItemStack inserted) {
            this.inserted = cloneItem(inserted);
        }

        private boolean claimTarget() { return gate.claimTarget(); }
        private boolean claimReturn() { return gate.claimInsertedReturn(); }
        private void complete(final ItemStack displaced) {
            this.displaced = cloneItem(displaced);
            gate.completeTargetWrite();
        }
        private InventoryEscrowGate.State state() { return gate.state(); }
        private boolean claimDisplacedReturn() { return gate.claimDisplacedReturn(); }
    }

    private final JavaPlugin plugin;
    private final MessageManager messages;
    private final ModerationManager moderationManager;
    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, List<ItemStack>> pendingReturns = new ConcurrentHashMap<>();
    private volatile boolean shuttingDown;

    public InvseeManager(final JavaPlugin plugin, final MessageManager messages,
                         final ModerationManager moderationManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.moderationManager = moderationManager;
    }

    public void open(final Player viewer, final Player target, final InvseeHolder.Mode mode,
                     final InvseeHolder.View view) {
        if (viewer.getUniqueId().equals(target.getUniqueId())) {
            viewer.sendMessage(messages.get("moderation.invsee-self", "&cA saját inventorydat nem nyithatod meg adminnézetben."));
            return;
        }
        closeSession(viewer.getUniqueId());
        final Session session = new Session(viewer.getUniqueId(), viewer.getName(), target.getUniqueId(),
                target.getName(), mode, view);
        sessions.put(viewer.getUniqueId(), session);
        capture(target, snapshot -> viewer.getScheduler().run(plugin, task -> {
            if (!viewer.isOnline() || sessions.get(viewer.getUniqueId()) != session) {
                return;
            }
            final InvseeHolder holder = new InvseeHolder(session.sessionId, session.targetId,
                    session.targetName, session.view, session.mode);
            viewer.openInventory(InvseeGUI.create(holder, snapshot, messages));
            startRefresh(viewer, session);
        }, () -> closeSession(viewer.getUniqueId())), () -> {
            sessions.remove(viewer.getUniqueId(), session);
            viewer.getScheduler().run(plugin, task -> viewer.sendMessage(messages.get(
                    "moderation.invsee-target-left", "&cA céljátékos kilépett.")), null);
        });
    }

    private void startRefresh(final Player viewer, final Session session) {
        if (session.refreshTask != null) {
            session.refreshTask.cancel();
        }
        session.refreshTask = viewer.getScheduler().runAtFixedRate(plugin,
                task -> requestRefresh(viewer, session), () -> closeSession(viewer.getUniqueId()),
                10L, 10L);
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
        capture(target, snapshot -> viewer.getScheduler().run(plugin, task -> {
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
            viewer.getScheduler().run(plugin, task -> closeFromViewerThread(viewer, session,
                    "&cA céljátékos kilépett."), null);
        });
    }

    private void capture(final Player target, final java.util.function.Consumer<Snapshot> callback,
                         final Runnable retired) {
        target.getScheduler().run(plugin, task -> {
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
        capture(target, snapshot -> viewer.getScheduler().run(plugin, task -> {
            if (!viewer.isOnline() || sessions.get(viewer.getUniqueId()) != session) {
                return;
            }
            final InvseeHolder next = new InvseeHolder(session.sessionId, session.targetId, session.targetName,
                    session.view, session.mode);
            viewer.openInventory(InvseeGUI.create(next, snapshot, messages));
            session.transitioning = false;
        }, () -> session.transitioning = false), () -> viewer.getScheduler().run(plugin,
                task -> closeFromViewerThread(viewer, session, "&cA céljátékos kilépett."), null));
    }

    public void beginSwap(final Player viewer, final InvseeHolder holder, final int rawSlot) {
        final Session session = validSession(viewer, holder);
        if (session == null || session.mode != InvseeHolder.Mode.EDIT
                || !viewer.hasPermission(Permissions.MODERATION_INVENTORY_EDIT)
                || session.pending != null) {
            return;
        }
        final TargetSlot targetSlot = mapSlot(holder.view(), rawSlot);
        if (targetSlot == null) {
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
        target.getScheduler().run(plugin, task -> {
            if (!target.isOnline() || !transfer.claimTarget()) {
                if (transfer.state() == InventoryEscrowGate.State.INIT) {
                    failBeforeTarget(viewer, session, transfer);
                }
                return;
            }
            final ItemStack displaced = readTargetSlot(target, targetSlot);
            writeTargetSlot(target, targetSlot, transfer.inserted);
            transfer.complete(displaced);
            moderationManager.logInventoryEditAsync(session.viewerId, session.viewerName,
                    target.getUniqueId(), target.getName(), holder.view().name(), rawSlot,
                    transfer.inserted, displaced);
            viewer.getScheduler().run(plugin, callback -> finishAfterTarget(viewer, session, transfer),
                    () -> queueDisplaced(session.viewerId, transfer));
        }, () -> failBeforeTarget(viewer, session, transfer));
    }

    private void finishAfterTarget(final Player viewer, final Session session, final PendingTransfer transfer) {
        if (!viewer.isOnline() || sessions.get(viewer.getUniqueId()) != session || transfer.viewerAbsent) {
            queueDisplaced(session.viewerId, transfer);
            return;
        }
        session.pending = null;
        if (transfer.claimDisplacedReturn()) {
            returnItem(viewer, transfer.displaced, true);
        }
        requestRefresh(viewer, session);
    }

    private void failBeforeTarget(final Player viewer, final Session session, final PendingTransfer transfer) {
        if (!transfer.claimReturn()) {
            return;
        }
        viewer.getScheduler().run(plugin, task -> {
            if (viewer.isOnline()) {
                returnItem(viewer, transfer.inserted, true);
                if (sessions.get(viewer.getUniqueId()) == session) {
                    session.pending = null;
                }
                viewer.sendMessage(messages.get("moderation.invsee-edit-failed",
                        "&cA céljátékos kilépett; a kurzortárgyad visszakaptad."));
            } else {
                queueReturn(session.viewerId, transfer.inserted);
            }
        }, () -> queueReturn(session.viewerId, transfer.inserted));
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
        viewer.getScheduler().run(plugin, task -> cleanupSession(session, viewer),
                () -> cleanupSession(session, null));
    }

    private void cleanupSession(final Session session, final Player viewer) {
        if (session.refreshTask != null) {
            session.refreshTask.cancel();
        }
        final PendingTransfer transfer = session.pending;
        session.pending = null;
        if (transfer == null) {
            return;
        }
        transfer.viewerAbsent = true;
        if (transfer.claimReturn()) {
            if (viewer != null && viewer.isOnline()) {
                returnItem(viewer, transfer.inserted, false);
            } else {
                queueReturn(session.viewerId, transfer.inserted);
            }
        } else if (transfer.state() == InventoryEscrowGate.State.COMPLETE) {
            if (viewer != null && viewer.isOnline() && transfer.claimDisplacedReturn()) {
                returnItem(viewer, transfer.displaced, false);
            } else {
                queueDisplaced(session.viewerId, transfer);
            }
        }
    }

    private void queueDisplaced(final UUID viewerId, final PendingTransfer transfer) {
        if (transfer.claimDisplacedReturn()) {
            queueReturn(viewerId, transfer.displaced);
        }
    }

    private void queueReturn(final UUID viewerId, final ItemStack item) {
        if (isEmpty(item)) {
            return;
        }
        pendingReturns.compute(viewerId, (ignored, current) -> {
            final List<ItemStack> copy = current == null ? new ArrayList<>() : new ArrayList<>(current);
            copy.add(item.clone());
            return List.copyOf(copy);
        });
    }

    /** Restores edit escrow on reconnect, on the player's own scheduler. */
    public void restorePending(final Player player) {
        final List<ItemStack> returns = pendingReturns.remove(player.getUniqueId());
        if (returns == null || returns.isEmpty()) {
            return;
        }
        for (final ItemStack item : returns) {
            returnItem(player, item, false);
        }
        player.sendMessage(messages.get("moderation.invsee-escrow-restored",
                "&aA megszakadt inventory-szerkesztés tárgyai visszakerültek hozzád."));
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
                    viewer.getScheduler().run(plugin, task -> {
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

    public void shutdown() {
        closeAll(true);
    }

    private void closeAll(final boolean terminal) {
        if (terminal) {
            shuttingDown = true;
        }
        for (final Session session : List.copyOf(sessions.values())) {
            if (sessions.remove(session.viewerId, session)) {
                final Player viewer = Bukkit.getPlayer(session.viewerId);
                if (viewer != null && viewer.isOnline()) {
                    viewer.getScheduler().run(plugin, task -> {
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

    private static boolean isEmpty(final ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }
}
