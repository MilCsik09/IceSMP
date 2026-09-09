package hu.taliann.icesmp.trash;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Native relic activation coordination; history, fields and tracking keep their existing authorities. */
public final class TrashRelicActivationService {
    interface InventoryOwner {
        boolean schedule(Runnable action, Runnable retired);
        boolean admitted();
        TrashHistoryStore.WallReceipt consume(BooleanSupplier admission);
    }
    interface ProjectileOwner {
        boolean schedule(Runnable action, Runnable retired);
        boolean admitted();
        boolean removeObserved();
    }

    private final TrashRuleFieldService fields;
    private final TrashRelicPolicy.ProjectileTracking tracking;
    private final BooleanSupplier open;
    private final Consumer<TrashHistoryStore.WallReceipt> acknowledge;
    private final Runnable unresolved;

    TrashRelicActivationService(TrashRuleFieldService fields, TrashRelicPolicy.ProjectileTracking tracking,
            BooleanSupplier open, Consumer<TrashHistoryStore.WallReceipt> acknowledge, Runnable unresolved) {
        this.fields = Objects.requireNonNull(fields); this.tracking = Objects.requireNonNull(tracking);
        this.open = Objects.requireNonNull(open); this.acknowledge = Objects.requireNonNull(acknowledge);
        this.unresolved = Objects.requireNonNull(unresolved);
    }

    /** Transfers the exact claim/ticket to bounded native owner callbacks; no entity is frozen. */
    boolean dispatchWall(TrashRuleFieldService.FieldClaim claim, TrashRelicPolicy.ProjectileTracking.Ticket ticket,
                         InventoryOwner inventory, ProjectileOwner projectile) {
        return new WallOperation(claim, ticket, inventory, projectile).dispatch();
    }

    private final class WallOperation {
        private final TrashRuleFieldService.FieldClaim claim;
        private final TrashRelicPolicy.ProjectileTracking.Ticket ticket;
        private final InventoryOwner inventory;
        private final ProjectileOwner projectile;
        private final AtomicBoolean inventoryEntered = new AtomicBoolean();
        private final AtomicBoolean projectileEntered = new AtomicBoolean();
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AtomicBoolean retired = new AtomicBoolean();
        private final AtomicBoolean reported = new AtomicBoolean();
        private volatile TrashHistoryStore.WallReceipt receipt;
        private volatile boolean observed;

        private WallOperation(TrashRuleFieldService.FieldClaim claim, TrashRelicPolicy.ProjectileTracking.Ticket ticket,
                              InventoryOwner inventory, ProjectileOwner projectile) {
            this.claim = Objects.requireNonNull(claim); this.ticket = Objects.requireNonNull(ticket);
            this.inventory = Objects.requireNonNull(inventory); this.projectile = Objects.requireNonNull(projectile);
        }
        private boolean admitted() {
            return !finished.get() && !retired.get() && open.getAsBoolean() && tracking.active(ticket) && fields.isClaimed(claim);
        }
        private boolean dispatch() {
            boolean queued = false;
            try {
                queued = admitted() && inventory.schedule(this::consumeOnOwner, this::retire);
                return queued;
            } finally { if (!queued) finish(); }
        }
        private void consumeOnOwner() {
            if (!inventoryEntered.compareAndSet(false, true)) return;
            boolean queued = false;
            try {
                if (!admitted() || !inventory.admitted()) return;
                receipt = inventory.consume(() -> admitted() && inventory.admitted());
                if (receipt == null) return;
                if (!receipt.field().equals(claim.field()) || receipt.removalObserved()) {
                    throw new IllegalStateException("Native wall consume returned a mismatched receipt");
                }
                // The durable consume may outlive the owner, field or plugin. Its receipt is retained.
                if (!admitted()) return;
                queued = projectile.schedule(this::removeOnOwner, this::retire);
            } finally { if (!queued) finish(); }
        }
        private void removeOnOwner() {
            if (!projectileEntered.compareAndSet(false, true)) return;
            try {
                final var result = fields.tryObserveClaimedEffect(claim,
                        () -> admitted() && projectile.admitted(), projectile::removeObserved);
                if (result.isEmpty()) return;
                observed = result.orElseThrow();
                if (observed) acknowledge.accept(receipt);
            } finally { finish(); }
        }
        private void finish() {
            // A retired callback can race an entered WAL acknowledgement. Exact claim identity
            // makes this late cleanup harmless even if another admission now owns the same field.
            if (receipt != null) fields.removeClaimed(claim);
            if (receipt != null && !observed && reported.compareAndSet(false, true)) unresolved.run();
            if (!finished.compareAndSet(false, true)) return;
            if (receipt == null) fields.releaseClaim(claim);
            tracking.release(ticket);
        }
        private void retire() {
            retired.set(true);
            // Removal itself may retire scheduler work. Its entered bounded callback owns the
            // observation/finally boundary; retirement must not fabricate an unobserved outcome.
            if (!projectileEntered.get()) finish();
        }
    }
}
