package hu.taliann.icesmp.trash;

import java.util.Objects;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import hu.taliann.icesmp.integrity.GameplayEffectPermit;
import hu.taliann.icesmp.integrity.RewardSource;

/** Native relic activation coordination; history, fields and tracking keep their existing authorities. */
public final class TrashRelicActivationService {
    record WallPermits(GameplayEffectPermit consumption, GameplayEffectPermit removal) {
        WallPermits { Objects.requireNonNull(consumption); Objects.requireNonNull(removal); }
    }
    interface DeadlineScheduler {
        Runnable schedule(long delayMillis, Runnable expired);
    }
    interface InventoryOwner {
        boolean schedule(Runnable action, Runnable retired);
        boolean admitted();
        CompletionStage<WallPermits> prepare();
        List<RewardSource> sources();
        TrashHistoryStore.WallReceipt consume(BooleanSupplier admission, BooleanSupplier finalAdmission);
    }
    interface ProjectileOwner {
        boolean schedule(Runnable action, Runnable retired);
        boolean admitted();
        List<RewardSource> sources();
        boolean removeObserved();
    }

    private final TrashRuleFieldService fields;
    private final TrashRelicPolicy.ProjectileTracking tracking;
    private final BooleanSupplier open;
    private final Consumer<TrashHistoryStore.WallReceipt> acknowledge;
    private final Runnable unresolved;
    private final DeadlineScheduler deadlines;

    TrashRelicActivationService(TrashRuleFieldService fields, TrashRelicPolicy.ProjectileTracking tracking,
            BooleanSupplier open, Consumer<TrashHistoryStore.WallReceipt> acknowledge, Runnable unresolved,
            DeadlineScheduler deadlines) {
        this.fields = Objects.requireNonNull(fields); this.tracking = Objects.requireNonNull(tracking);
        this.open = Objects.requireNonNull(open); this.acknowledge = Objects.requireNonNull(acknowledge);
        this.unresolved = Objects.requireNonNull(unresolved);
        this.deadlines = Objects.requireNonNull(deadlines);
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
        private final AtomicBoolean inventoryCompleted = new AtomicBoolean();
        private final AtomicBoolean preparationEntered = new AtomicBoolean();
        private final AtomicReference<Runnable> cancelDeadline = new AtomicReference<>();
        private final AtomicBoolean projectileEntered = new AtomicBoolean();
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AtomicBoolean retired = new AtomicBoolean();
        private final AtomicBoolean reported = new AtomicBoolean();
        private volatile TrashHistoryStore.WallReceipt receipt;
        private volatile boolean observed;
        private volatile WallPermits permits;

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
                if (!admitted()) return false;
                final Runnable cancel = Objects.requireNonNull(deadlines.schedule(5000, this::retire));
                cancelDeadline.set(cancel);
                if (finished.get()) cancelDeadline();
                queued = admitted() && inventory.schedule(this::prepareOnOwner, this::retire);
                return queued;
            } finally { if (!queued) finish(); }
        }
        private void prepareOnOwner() {
            if (!preparationEntered.compareAndSet(false, true)) return;
            boolean waiting = false;
            try {
                if (!admitted() || !inventory.admitted()) return;
                final var preparation = Objects.requireNonNull(inventory.prepare());
                preparation.whenComplete((ready, failure) -> {
                    boolean queued = false;
                    try {
                        if (failure != null) { report(); return; }
                        if (ready == null || !admitted()) return;
                        permits = ready;
                        queued = inventory.schedule(this::consumeOnOwner, this::retire);
                    } catch (RuntimeException | Error rejected) { report(); }
                    finally { if (!queued) finish(); }
                });
                waiting = true;
            } catch (RuntimeException | Error rejected) { report(); }
            finally { if (!waiting) finish(); }
        }
        private void consumeOnOwner() {
            if (!inventoryEntered.compareAndSet(false, true)) return;
            boolean queued = false;
            try {
                if (!admitted() || !inventory.admitted()) return;
                receipt = inventory.consume(() -> admitted() && inventory.admitted(),
                        () -> permits.consumption().claim(inventory.sources()));
                if (receipt == null) return;
                if (!receipt.field().equals(claim.field()) || receipt.removalObserved()) {
                    throw new IllegalStateException("Native wall consume returned a mismatched receipt");
                }
                // The durable consume may outlive the owner, field or plugin. Its receipt is retained.
                if (!admitted()) return;
                queued = projectile.schedule(this::removeOnOwner, this::retire);
            } finally {
                inventoryCompleted.set(true);
                if (!queued || retired.get() && !projectileEntered.get()) finish();
            }
        }
        private void removeOnOwner() {
            if (!projectileEntered.compareAndSet(false, true)) return;
            try {
                final var result = fields.tryObserveClaimedEffect(claim,
                        () -> admitted() && projectile.admitted()
                                && permits.removal().claim(projectile.sources()), projectile::removeObserved);
                if (result.isEmpty()) return;
                observed = result.orElseThrow();
                if (observed) acknowledge.accept(receipt);
            } finally { finish(); }
        }
        private void finish() {
            // A retired callback can race an entered WAL acknowledgement. Exact claim identity
            // makes this late cleanup harmless even if another admission now owns the same field.
            if (receipt != null) fields.removeClaimed(claim);
            if (receipt != null && !observed) report();
            if (!finished.compareAndSet(false, true)) return;
            if (receipt == null) fields.releaseClaim(claim);
            tracking.release(ticket);
            cancelDeadline();
        }
        private void cancelDeadline() {
            final Runnable cancel = cancelDeadline.getAndSet(null);
            if (cancel != null) cancel.run();
        }
        private void report() { if (reported.compareAndSet(false, true)) unresolved.run(); }
        private void retire() {
            retired.set(true);
            // Removal itself may retire scheduler work. Its entered bounded callback owns the
            // observation/finally boundary; retirement must not fabricate an unobserved outcome.
            // An entered native write keeps its capacity and claim until its real acknowledgement.
            if (!projectileEntered.get() && (!inventoryEntered.get() || inventoryCompleted.get())) finish();
        }
    }
}
