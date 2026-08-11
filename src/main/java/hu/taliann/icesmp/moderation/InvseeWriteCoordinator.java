package hu.taliann.icesmp.moderation;

import hu.taliann.icesmp.core.Permissions;
import hu.taliann.icesmp.gui.InvseeHolder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Chooses the automatic /invsee mode and keeps one write lease per target.
 *
 * <p>The manager's existing escrow remains the authority for item ownership.
 * This coordinator only controls whether a GUI may submit edits.</p>
 */
public final class InvseeWriteCoordinator {

    private static final InventoryWriteLock LOCK = new InventoryWriteLock();

    private InvseeWriteCoordinator() {
    }

    public static InvseeHolder.Mode chooseMode(final Player viewer, final Player target) {
        if (!viewer.hasPermission(Permissions.MODERATION_INVENTORY_EDIT)) {
            LOCK.releaseViewer(viewer.getUniqueId());
            return InvseeHolder.Mode.READ_ONLY;
        }
        return LOCK.acquire(viewer.getUniqueId(), target.getUniqueId())
                ? InvseeHolder.Mode.EDIT : InvseeHolder.Mode.READ_ONLY;
    }

    /**
     * Releases a provisional lease if opening the real editor failed before the holder appeared.
     */
    public static void verifyOpened(final Player viewer, final UUID targetId,
                                    final InvseeHolder.Mode mode) {
        if (mode != InvseeHolder.Mode.EDIT) {
            return;
        }
        final JavaPlugin plugin = JavaPlugin.getProvidingPlugin(InvseeWriteCoordinator.class);
        try {
            viewer.getScheduler().runDelayed(plugin, task -> {
                if (!viewer.isOnline()
                        || !(viewer.getOpenInventory().getTopInventory().getHolder()
                        instanceof InvseeHolder holder)
                        || holder.mode() != InvseeHolder.Mode.EDIT
                        || !holder.targetId().equals(targetId)
                        || !LOCK.holds(viewer.getUniqueId(), targetId)) {
                    LOCK.release(viewer.getUniqueId(), targetId);
                }
            }, () -> LOCK.release(viewer.getUniqueId(), targetId), 100L);
        } catch (final RuntimeException unavailable) {
            LOCK.release(viewer.getUniqueId(), targetId);
        }
    }

    /**
     * Inventory view transitions close the old holder before opening the next one.
     * The one-tick check preserves the lease for MAIN <-> ENDER transitions but
     * releases it after an actual close.
     */
    public static void releaseAfterClose(final Player viewer, final InvseeHolder closedHolder) {
        if (closedHolder.mode() != InvseeHolder.Mode.EDIT) {
            return;
        }
        final JavaPlugin plugin = JavaPlugin.getProvidingPlugin(InvseeWriteCoordinator.class);
        final UUID viewerId = viewer.getUniqueId();
        final UUID targetId = closedHolder.targetId();
        try {
            viewer.getScheduler().runDelayed(plugin, task -> {
                if (viewer.isOnline()
                        && viewer.getOpenInventory().getTopInventory().getHolder()
                        instanceof InvseeHolder current
                        && current.mode() == InvseeHolder.Mode.EDIT
                        && current.targetId().equals(targetId)
                        && LOCK.holds(viewerId, targetId)) {
                    return;
                }
                LOCK.release(viewerId, targetId);
            }, () -> LOCK.release(viewerId, targetId), 1L);
        } catch (final RuntimeException unavailable) {
            LOCK.release(viewerId, targetId);
        }
    }


    public static boolean canWrite(final Player viewer, final UUID targetId) {
        if (viewer == null || !viewer.isOnline()
                || !viewer.hasPermission(Permissions.MODERATION_INVENTORY_EDIT)) {
            if (viewer != null) {
                LOCK.release(viewer.getUniqueId(), targetId);
            }
            return false;
        }
        return LOCK.holds(viewer.getUniqueId(), targetId);
    }

    /** A disconnect can represent either the writer or the inspected target. */
    public static void releasePlayer(final UUID playerId) {
        LOCK.releaseViewer(playerId);
        LOCK.releaseTarget(playerId);
    }

    public static boolean hasWriter(final UUID targetId) {
        return LOCK.writer(targetId).isPresent();
    }

    public static void reset() {
        LOCK.clear();
    }
}
