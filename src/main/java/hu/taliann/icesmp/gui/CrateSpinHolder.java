package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.crates.CrateTaskLease;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Owner-scoped holder and race-safe task lease for the cosmetic crate reel. */
public final class CrateSpinHolder implements InventoryHolder {

    private final UUID ownerUuid;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean completed = new AtomicBoolean();
    private final AtomicReference<CrateTaskLease> taskLease = new AtomicReference<>();
    private volatile Inventory inventory;

    public CrateSpinHolder(final UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    public Inventory inventoryOrNull() {
        return inventory;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    /** Publishes the next delayed-task lease and retires the already completed prior step. */
    public boolean replaceTaskLease(final CrateTaskLease next) {
        if (next == null || cancelled.get()) {
            if (next != null) {
                next.retire();
            }
            return false;
        }
        final CrateTaskLease previous = taskLease.getAndSet(next);
        if (previous != null) {
            previous.retire();
        }
        if (cancelled.get() && taskLease.compareAndSet(next, null)) {
            next.retire();
            return false;
        }
        return true;
    }

    /** Idempotent rejection/close/quit cleanup. */
    public void cancel() {
        cancelled.set(true);
        final CrateTaskLease current = taskLease.getAndSet(null);
        if (current != null) {
            current.retire();
        }
    }

    /** Runs the cosmetic completion callback at most once. */
    public boolean complete(final Runnable action) {
        if (!completed.compareAndSet(false, true)) {
            return false;
        }
        cancel();
        action.run();
        return true;
    }

    @Override
    public Inventory getInventory() {
        final Inventory current = inventory;
        return current == null ? Bukkit.createInventory(this, 27) : current;
    }
}
